# clj-socketio-client

A Clojure library that wraps https://github.com/socketio/socket.io-client-java

## Installation

### Leiningen

```
[clj-socketio-client "0.1.0-SNAPSHOT"]
```

## Usage

The Clojure wrapper provides a thin convenience layer atop the Java client.

```
(:require [clj-socketio-client.core :as sio])

(def s (sio/make-socket "http://host:port" event-map))
(sio/emit! s "message" args optional-unique-id)
```
The event map provided to `make-socket` maps standard or custom socket.io event names to variadic [& args] functions, which will be called asynchronously when messages are received.  

The `args` argument to `emit!` may be a map, a sequential object (list, vector, ...) or a primitive (string, number, true false).  The arguments are JSON-encoded into an array for transmission.

For convenience, the following are also available:

`(sio/make-pass-take-socket url)` constructs a socket with a predefined event map.  When the socket is opened, it sends a "join" message, with a unique identifier.  

`(sio/pass-take msg)` emits a `"pass"` message with the given `msg` arguments, and (synchronously) returns a Clojure promise to the value of a corresponding received `"take"` message from the remote service.  The promise times out if not delivered within 60 seconds.

## License

Copyright © 2016 i2k Connect LLC

Distributed under the MIT License.
