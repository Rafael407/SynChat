# SynChat

A desktop instant-messaging application that simulates a modern chat platform on a
single machine. A multi-threaded TCP server handles concurrent clients, authentication,
friend requests and real-time private message routing; a JavaFX client talks to it over
a line-based JSON protocol.

| Layer | Technology |
|---|---|
| Language | Java 17 |
| GUI | JavaFX 21 (pure Java, no CSS) |
| Networking | Java TCP sockets (`ServerSocket` / `Socket`) |
| Concurrency | `ExecutorService` fixed thread pool + a client listener thread |
| Database | SQLite |
| DB API | JDBC (`org.xerial:sqlite-jdbc`) |
| Build | Maven |
| Wire format | JSON (Gson), one document per line |
| Server address | `127.0.0.1:5555` (localhost only) |

---

## 1. Running it

Requirements: JDK 17 or newer, Maven 3.8+.

```bash
mvn clean compile        # resolves JavaFX, Gson and sqlite-jdbc
```

**Terminal 1 - start the server**

```bash
mvn exec:java
# optional: mvn exec:java -Dexec.args="5555 synchat.db"
```

It creates `synchat.db` in the project folder on first run and prints:

```
[DB ] schema ready -> synchat.db
[SRV] SynChat server listening on 127.0.0.1:5555
[SRV] worker pool size = 20
```

**Terminal 2 and 3 - start two clients**

```bash
mvn javafx:run
```

Run that command twice to get two windows and chat between two accounts.

In IntelliJ IDEA: open the folder as a Maven project, then create two run
configurations — `com.synchat.server.ServerMain` and `com.synchat.client.ClientApp`
(tick *Allow multiple instances* on the client one).

### Quick demo

1. Client A → **Create account** → register `alice`, log in.
2. Client B → register `bob`, log in.
3. On B: **Search** tab → type `ali` → **Go** → select alice → **Send friend request**.
4. On A: the **Requests (1)** tab lights up immediately → select → **Accept**.
5. Both sides now see each other under **Friends** with a `●` when online.
6. Click the friend, type, press Enter. The message appears on the other window
   with no refresh — that is the listener thread at work.
7. Close B. A sees `○ bob`. Send messages to bob anyway; when B logs back in the
   queued messages are delivered.

---

## 2. Server architecture

```
ServerMain
    |
    +-- Database.init()            creates the SQLite schema if missing
    |
    +-- ChatServer.start()
            |
            |  main thread: accept() loop
            v
        ExecutorService  (fixed pool, MAX_CONCURRENT_CLIENTS = 20)
            |
            +-- ClientHandler #1   owns one socket, one session
            +-- ClientHandler #2
            +-- ...
                    |
                    +-- SessionManager   ConcurrentHashMap<userId, ClientHandler>
                    +-- UserDao / FriendDao / MessageDao  -> JDBC -> SQLite
```

* The accept loop never does work itself — it hands every accepted socket to the pool.
* The pool is **bounded on purpose**. Connection 21 waits in the queue until a worker
  frees up, so the process can never be pushed into spawning unbounded threads.
* `ClientHandler.send()` is `synchronized`, because a handler's socket is written to by
  *other* handler threads whenever they route a message to that user.
* `SessionManager` is the routing table. It is the piece that answers *"from whom, to
  whom"*: the sender is the handler's own authenticated user, the receiver is looked up
  by username in the DB and then by id in this map.
* DAOs are stateless and open a short-lived JDBC connection per call. The database runs
  in WAL mode so readers never block on the writer.

### Message routing path

```
Alice's client  --REQ_SEND_MESSAGE{to:"bob"}-->  Alice's ClientHandler
                                                     |
                                     UserDao.findByUsername("bob")   -> id 7
                                     FriendDao.areFriends(alice,7)   -> true
                                     MessageDao.save(..., delivered) -> row
                                                     |
                                     SessionManager.get(7)  ---> Bob's ClientHandler
                                                     |                  |
                          RESPONSE{ok,message} back to Alice     EVT_MESSAGE pushed to Bob
```

If Bob is offline the row is stored with `delivered = 0` and replayed to him the moment
he logs in.

---

## 3. Client architecture

```
JavaFX Application Thread          ClientConnection            listener thread
        |                                |                            |
   button click                          |                            |
        |-- request(Packet) ------------>|-- socket write ----------->  (server)
        |   returns a CompletableFuture  |                            |
        |                                |<-- readLine() --------------|
        |                                |                            |
        |<-- Platform.runLater(callback) | id matches a pending future?
                                         |   yes -> complete it
                                         |   no  -> it is a server push,
                                         |          fire event listeners
```

* The UI thread **never blocks on I/O**. Every call returns a `CompletableFuture` and the
  result is delivered back on the FX thread.
* The listener thread is the "real-time receiving" thread required by the spec: an
  infinite `readLine()` loop that classifies each packet as a correlated response or an
  unsolicited event.
* Views (`LoginView`, `RegisterView`, `MainView`) only register event handlers; they know
  nothing about sockets.

---

## 4. Messaging protocol

Transport: plain TCP, UTF-8, **one JSON document per line** terminated by `\n`.
Every packet has the same envelope:

```json
{ "type": "SEND_MESSAGE", "id": "c-14", "data": { "to": "bob", "content": "hi" } }
```

* `type` — the operation.
* `id` — correlation id generated by the client. The server copies it into its answer so
  the client can match the reply to the request. `null` on server-initiated pushes.
* `data` — payload object.

Every answer to a request has `type = "RESPONSE"` and always carries `data.ok`; when
`ok` is `false`, `data.message` explains why.

### Client → server

| type | data in | data out on success |
|---|---|---|
| `REGISTER` | `username`, `password` | `message` |
| `LOGIN` | `username`, `password` | `username`, `userId` |
| `LOGOUT` | – | `message` |
| `CHECK_USERNAME` | `username` | `available`, `message` |
| `CHANGE_PASSWORD` | `oldPassword`, `newPassword` | `message` |
| `SEARCH_USER` | `query` | `users[]` |
| `FRIEND_ADD` | `username` | `message` |
| `FRIEND_RESPOND` | `requestId`, `accept` | `message` |
| `FRIEND_LIST` | – | `friends[]` |
| `REQUEST_LIST` | – | `requests[]` |
| `SEND_MESSAGE` | `to`, `content` | `message` (the stored message) |
| `HISTORY` | `peer` | `messages[]` |

### Server → client pushes (`id` is null)

| type | data |
|---|---|
| `EVT_MESSAGE` | `message` — a new private message |
| `EVT_FRIEND_REQUEST` | `request` — someone wants to be your friend |
| `EVT_FRIEND_RESULT` | `username`, `accepted` |
| `EVT_PRESENCE` | `username`, `online` |
| `EVT_SERVER_NOTICE` | `message` |

### Example exchange

```
C -> {"type":"LOGIN","id":"c-1","data":{"username":"alice","password":"secret"}}
S -> {"type":"RESPONSE","id":"c-1","data":{"ok":true,"username":"alice","userId":1}}

C -> {"type":"SEND_MESSAGE","id":"c-2","data":{"to":"bob","content":"hey"}}
S -> {"type":"RESPONSE","id":"c-2","data":{"ok":true,"message":{...}}}

           ... meanwhile, in Bob's client ...
S -> {"type":"EVT_MESSAGE","data":{"message":{"id":9,"from":"alice","to":"bob",
                                              "content":"hey","sentAt":"2026-09-21 14:03:11"}}}
```

---

## 5. Database schema

```sql
users(id, username UNIQUE COLLATE NOCASE, password_hash, salt, created_at)
friend_requests(id, sender_id, receiver_id, status PENDING|ACCEPTED|REJECTED,
                created_at, UNIQUE(sender_id, receiver_id))
friends(user_id, friend_id, since, PRIMARY KEY(user_id, friend_id))
messages(id, sender_id, receiver_id, content, sent_at, delivered)
```

* Friendship is stored in both directions so "who are my friends" is a single indexed
  lookup with no `OR`.
* Passwords are never stored in clear text: each account gets a random 16-byte salt and
  the table keeps `SHA-256(salt || password)`. Verification uses a constant-time compare.
* Accepting a request updates the request row and inserts both friendship rows inside one
  transaction, so a half-built friendship can never be observed.
* `delivered = 0` marks the offline queue.

---

## 6. Project layout

```
src/main/java/com/synchat
├── common
│   ├── Protocol.java          all wire constants + the protocol contract
│   ├── Packet.java            the JSON envelope
│   ├── JsonUtil.java
│   └── dto                    UserDto, FriendRequestDto, ChatMessageDto
├── server
│   ├── ServerMain.java        entry point
│   ├── ChatServer.java        accept loop + bounded thread pool
│   ├── ClientHandler.java     one session, request dispatch, routing
│   ├── SessionManager.java    userId -> handler routing table
│   ├── db                     Database, UserDao, FriendDao, MessageDao
│   └── util/PasswordUtil.java salted SHA-256
└── client
    ├── ClientApp.java         JavaFX entry point + navigation
    ├── net/ClientConnection.java  socket, listener thread, futures, events
    └── ui                     LoginView, RegisterView, MainView, Dialogs
```

---

## 7. Git

```bash
git init
git add .
git commit -m "SynChat: multi-threaded chat server and JavaFX client"
git branch -M main
git remote add origin https://github.com/<you>/synchat.git
git push -u origin main
```

`synchat.db`, `target/` and IDE folders are already excluded by `.gitignore`.

---

## 8. Things worth knowing

* The server binds to `127.0.0.1` explicitly, so it is unreachable from other machines
  even if the port is open.
* One account can only hold one live session; a second login attempt is rejected rather
  than silently kicking the first.
* Messages can only be sent between accepted friends — the server enforces this, not the
  UI.
* Password rules: username 3–20 chars of `A-Za-z0-9_.`, password at least 4 characters.
  Change them in `ClientHandler.validateCredentials`.
