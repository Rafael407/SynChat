package com.synchat.client.ui;

import com.synchat.client.ClientApp;
import com.synchat.client.Joke_api.Joke;
import com.synchat.client.Joke_api.JokeApiClient;
import com.synchat.client.net.ClientConnection;
import com.synchat.common.Packet;
import com.synchat.common.Protocol;
import com.synchat.common.dto.ChatMessageDto;
import com.synchat.common.dto.FriendRequestDto;
import com.synchat.common.dto.UserDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

public class MainView extends BorderPane {

    private final ClientApp app;
    private final ClientConnection connection;
    private final String me;

    private final ObservableList<UserDto> friends = FXCollections.observableArrayList();
    private final ObservableList<FriendRequestDto> requests = FXCollections.observableArrayList();
    private final ObservableList<UserDto> searchResults = FXCollections.observableArrayList();
    private final ObservableList<ChatMessageDto> messages = FXCollections.observableArrayList();

    private final ListView<UserDto> friendsView = new ListView<>(friends);
    private final ListView<FriendRequestDto> requestsView = new ListView<>(requests);
    private final ListView<UserDto> searchView = new ListView<>(searchResults);
    private final ListView<ChatMessageDto> messagesView = new ListView<>(messages);

    private final Label chatHeader = new Label("Pick a friend on the left to start chatting");
    private final Label statusLabel = new Label();
    private final Label requestsTabLabel = new Label("Requests");
    private final TextField messageField = new TextField();
    private final Button sendButton = new Button("Send");


    private String currentPeer;

    public MainView(ClientApp app, ClientConnection connection, String me) {
        this.app = app;
        this.connection = connection;
        this.me = me;

        setBackground(Theme.fill(Theme.SURFACE, 0));
        setTop(buildHeader());
        setLeft(buildSidebar());
        setCenter(buildChatPane());

        registerServerEvents();
        refreshFriends();
        refreshRequests();
    }

    private Region buildHeader() {
        Label brand = new Label("SynChat");
        Theme.titleStyle(brand, 16);

        Label who = new Label("Signed in as " + me);
        Theme.mutedStyle(who);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button changePassword = new Button("Change password");
        Theme.secondaryButton(changePassword);
        changePassword.setOnAction(e -> Dialogs.changePassword(connection));

        Button deleteAccount = new Button("Delete account");
        Theme.dangerButton(deleteAccount);
        deleteAccount.setOnAction(e -> Dialogs.deleteAccount(connection, () -> {
            connection.clearAllListeners();
            app.showLogin();
        }));

        Button logout = new Button("Log out");
        Theme.secondaryButton(logout);
        logout.setOnAction(e -> doLogout());

        HBox bar = new HBox(14, brand, who, spacer, changePassword, deleteAccount, logout);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setBackground(Theme.fill(Theme.ACCENT_LIGHT, 0));
        return bar;
    }

    private Region buildSidebar() {

        friendsView.setPlaceholder(new Label("No friends yet.\nFind people in the Search tab."));
        friendsView.setCellFactory(lv -> new ListCell<>() {
            private final Circle dot = new Circle(4);
            private final Label nameLabel = new Label();
            private final HBox row = new HBox(8, dot, nameLabel);

            {
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(UserDto user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                    return;
                }
                dot.setFill(user.online ? Theme.ONLINE : Theme.OFFLINE);
                nameLabel.setText(user.username);
                setGraphic(row);
            }
        });
        friendsView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                openConversation(selected.username);
            }
        });

        Button refreshFriends = new Button("Refresh");
        Theme.secondaryButton(refreshFriends);
        refreshFriends.setMaxWidth(Double.MAX_VALUE);
        refreshFriends.setOnAction(e -> refreshFriends());

        VBox friendsBox = new VBox(8, friendsView, refreshFriends);
        friendsBox.setPadding(new Insets(8));
        VBox.setVgrow(friendsView, Priority.ALWAYS);


        requestsView.setPlaceholder(new Label("No pending requests."));
        requestsView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FriendRequestDto r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.from);
            }
        });

        Button accept = new Button("Accept");
        Button reject = new Button("Reject");
        Theme.primaryButton(accept);
        Theme.secondaryButton(reject);
        accept.setOnAction(e -> respondToRequest(true));
        reject.setOnAction(e -> respondToRequest(false));
        HBox requestButtons = new HBox(8, accept, reject);
        requestButtons.setAlignment(Pos.CENTER);

        VBox requestsBox = new VBox(8, requestsView, requestButtons);
        requestsBox.setPadding(new Insets(8));
        VBox.setVgrow(requestsView, Priority.ALWAYS);

        TextField searchField = new TextField();
        searchField.setPromptText("Search username");
        Button searchButton = new Button("Go");
        Theme.primaryButton(searchButton);
        searchButton.setOnAction(e -> searchUsers(searchField.getText()));
        searchField.setOnAction(e -> searchUsers(searchField.getText()));
        HBox searchRow = new HBox(6, searchField, searchButton);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchView.setPlaceholder(new Label("No results."));
        searchView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(UserDto user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null
                        ? null
                        : user.username + (user.online ? "  (online)" : ""));
            }
        });

        Button addFriend = new Button("Send friend request");
        Theme.primaryButton(addFriend);
        addFriend.setMaxWidth(Double.MAX_VALUE);
        addFriend.setOnAction(e -> sendFriendRequest());

        VBox searchBox = new VBox(8, searchRow, searchView, addFriend);
        searchBox.setPadding(new Insets(8));
        VBox.setVgrow(searchView, Priority.ALWAYS);

        Tab tabFriends = new Tab("Friends", friendsBox);
        Tab tabRequests = new Tab();
        tabRequests.setGraphic(requestsTabLabel);
        tabRequests.setContent(requestsBox);
        Tab tabSearch = new Tab("Search", searchBox);
        for (Tab t : List.of(tabFriends, tabRequests, tabSearch)) {
            t.setClosable(false);
        }

        TabPane tabs = new TabPane(tabFriends, tabRequests, tabSearch);
        tabs.setPrefWidth(270);
        tabs.setMinWidth(230);
        return tabs;
    }

    private Region buildChatPane() {
        Theme.titleStyle(chatHeader, 16);

        messagesView.setPlaceholder(new Label("No messages yet."));
        messagesView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessageDto m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                boolean mine = m.from.equals(me);

                Label bubble = new Label("[" + m.shortTime() + "] " + m.content);
                bubble.setWrapText(true);
                bubble.maxWidthProperty().bind(lv.widthProperty().multiply(0.7));
                bubble.setPadding(new Insets(8, 12, 8, 12));
                bubble.setBackground(Theme.fill(mine ? Theme.BUBBLE_MINE : Theme.BUBBLE_THEIRS, 12));
                bubble.setTextFill(mine ? Color.WHITE : Color.web("#2B2D3A"));

                HBox row = new HBox(bubble);
                row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                row.setPadding(new Insets(3, 4, 3, 4));

                setGraphic(row);
                setText(null);
            }
        });

        messageField.setPromptText("Write a message and press Enter");
        messageField.setOnAction(e -> sendMessage());
        sendButton.setOnAction(e -> sendMessage());
        Theme.primaryButton(sendButton);

        Button icebreakerButton = new Button("🎲 Icebreaker");
        Theme.secondaryButton(icebreakerButton);
        icebreakerButton.setOnAction(e -> fetchIcebreaker());

        HBox inputRow = new HBox(8, messageField, icebreakerButton, sendButton);
        inputRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        setChatEnabled(false);

        VBox box = new VBox(8, chatHeader, messagesView, inputRow, statusLabel);
        box.setPadding(new Insets(14));
        box.setBackground(Theme.fill(Color.WHITE, 0));
        VBox.setVgrow(messagesView, Priority.ALWAYS);
        return box;
    }

    private void setChatEnabled(boolean enabled) {
        messageField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    /*
     fetches a single joke from jokeapi.dev
     */
    private void fetchIcebreaker() {
        if (currentPeer == null) {
            status("Open a conversation with a friend first.");
            return;
        }
        status("Fetching a random icebreaker...");
        Thread worker = new Thread(() -> {
            try {
                Joke joke = JokeApiClient.fetchRandomJoke();
                Platform.runLater(() -> {
                    messageField.setText(joke.getJoke());
                    status("Icebreaker loaded — edit it or hit Enter to send.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status("Could not fetch an icebreaker: " + ex.getMessage()));
            }
        }, "icebreaker-fetch");
        worker.setDaemon(true);
        worker.start();
    }

    private void registerServerEvents() {
        connection.clearAllListeners();

        connection.on(Protocol.EVT_MESSAGE, packet -> {
            ChatMessageDto m = packet.getObject("message", ChatMessageDto.class);
            if (m == null) {
                return;
            }
            String peer = m.from.equals(me) ? m.to : m.from;
            if (peer.equals(currentPeer)) {
                appendMessage(m);
            } else {
                status("New message from " + m.from);
            }
        });

        connection.on(Protocol.EVT_FRIEND_REQUEST, packet -> {
            FriendRequestDto r = packet.getObject("request", FriendRequestDto.class);
            if (r != null) {
                requests.add(0, r);
                updateRequestBadge();
                status(r.from + " sent you a friend request");
            }
        });

        connection.on(Protocol.EVT_FRIEND_RESULT, packet -> {
            String who = packet.getString("username");
            boolean accepted = packet.getBoolean("accepted");
            status(accepted
                    ? who + " accepted your friend request"
                    : who + " rejected your friend request");
            if (accepted) {
                refreshFriends();
            }
        });

        connection.on(Protocol.EVT_FRIEND_REMOVED, packet -> {
            String who = packet.getString("username");
            friends.removeIf(f -> f.username.equals(who));
            status(who + " deleted their account");
            if (who != null && who.equals(currentPeer)) {
                currentPeer = null;
                chatHeader.setText("Pick a friend on the left to start chatting");
                messages.clear();
                setChatEnabled(false);
            }
        });

        connection.on(Protocol.EVT_PRESENCE, packet -> {
            String who = packet.getString("username");
            boolean online = packet.getBoolean("online");
            boolean known = false;
            for (UserDto f : friends) {
                if (f.username.equals(who)) {
                    f.online = online;
                    known = true;
                }
            }
            if (known) {
                friendsView.refresh();
            } else if (online) {
                refreshFriends();    // a brand new friendship
            }
        });

        connection.on(Protocol.EVT_SERVER_NOTICE, packet ->
                Dialogs.info("Server notice", packet.getString("message")));
    }


    private void refreshFriends() {
        connection.send(Packet.of(Protocol.REQ_FRIEND_LIST), response -> {
            if (response.isOk()) {
                friends.setAll(response.getList("friends", UserDto.class));
            } else {
                status(response.errorMessage());
            }
        });
    }

    private void refreshRequests() {
        connection.send(Packet.of(Protocol.REQ_REQUEST_LIST), response -> {
            if (response.isOk()) {
                requests.setAll(response.getList("requests", FriendRequestDto.class));
                updateRequestBadge();
            }
        });
    }

    private void updateRequestBadge() {
        requestsTabLabel.setText(requests.isEmpty() ? "Requests" : "Requests (" + requests.size() + ")");
    }

    private void searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            status("Type a username to search for.");
            return;
        }
        connection.send(Packet.of(Protocol.REQ_SEARCH_USER).put("query", query.trim()), response -> {
            if (response.isOk()) {
                searchResults.setAll(response.getList("users", UserDto.class));
                status(searchResults.isEmpty() ? "No user matched." : searchResults.size() + " user(s) found");
            } else {
                status(response.errorMessage());
            }
        });
    }

    private void sendFriendRequest() {
        UserDto selected = searchView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status("Select a user from the search results first.");
            return;
        }
        connection.send(Packet.of(Protocol.REQ_FRIEND_ADD).put("username", selected.username),
                response -> status(response.isOk() ? response.getString("message") : response.errorMessage()));
    }

    private void respondToRequest(boolean accept) {
        FriendRequestDto selected = requestsView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            status("Select a request first.");
            return;
        }
        connection.send(Packet.of(Protocol.REQ_FRIEND_RESPOND)
                .put("requestId", selected.id)
                .put("accept", accept), response -> {

            if (response.isOk()) {
                requests.remove(selected);
                updateRequestBadge();
                status(response.getString("message"));
                if (accept) {
                    refreshFriends();
                }
            } else {
                status(response.errorMessage());
            }
        });
    }

    private void openConversation(String peer) {
        currentPeer = peer;
        chatHeader.setText("Chat with " + peer);
        setChatEnabled(true);
        messages.clear();

        connection.send(Packet.of(Protocol.REQ_HISTORY).put("peer", peer), response -> {
            if (response.isOk()) {
                messages.setAll(response.getList("messages", ChatMessageDto.class));
                scrollToEnd();
            } else {
                status(response.errorMessage());
            }
        });
    }

    private void sendMessage() {
        String text = messageField.getText();
        if (currentPeer == null || text == null || text.isBlank()) {
            return;
        }
        messageField.clear();

        connection.send(Packet.of(Protocol.REQ_SEND_MESSAGE)
                .put("to", currentPeer)
                .put("content", text.trim()), response -> {

            if (response.isOk()) {
                ChatMessageDto sent = response.getObject("message", ChatMessageDto.class);
                if (sent != null && sent.to.equals(currentPeer)) {
                    appendMessage(sent);
                }
            } else {
                status(response.errorMessage());
                messageField.setText(text);
            }
        });
    }

    private void doLogout() {
        connection.send(Packet.of(Protocol.REQ_LOGOUT), response -> {
            connection.clearAllListeners();
            app.showLogin();
        });
    }

    private void appendMessage(ChatMessageDto m) {
        messages.add(m);
        scrollToEnd();
    }

    private void scrollToEnd() {
        if (!messages.isEmpty()) {
            messagesView.scrollTo(messages.size() - 1);
        }
    }

    private void status(String text) {
        statusLabel.setText(text == null ? "" : text);
    }
}