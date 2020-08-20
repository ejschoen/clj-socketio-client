(ns clj-socketio-client.core
  (:use [taoensso.timbre :only [set-level! debug warn info error]])
  (:require [clojure.pprint])
  (:require [taoensso.timbre.tools.logging])
  (:require [cheshire.core])
  (:import [org.json JSONObject]
           [io.socket.parser Parser]
           [io.socket.client IO Socket]
           [io.socket.emitter Emitter$Listener]))


;;(taoensso.timbre.tools.logging/use-timbre)

(set-level! :info)

(deftype Listener [callback]
  Emitter$Listener
  (call [& args]
    (apply callback args)))

(deftype Ack [callback]
  io.socket.client.Ack
  (call [& args]
    (apply callback args)))

(def default-event-map
  {Socket/EVENT_ERROR (fn [& args] (error (apply str args)))
   Socket/EVENT_MESSAGE (fn [& args] (info (apply str args)))
   Socket/EVENT_CONNECT_ERROR (fn [& args] (error (apply str args)))
   Socket/EVENT_CONNECT_TIMEOUT (fn [& args] (error (apply str args)))})

(defn connect!
  [socket]
  (.connect socket))

(defn disconnect!
  [socket]
  (.disconnect socket))

(defn opt-fn [opt-key]
  (cond
    (= :path opt-key) (fn [io-options val]
                        (set! (.-path io-options) val))
    (= :transports opt-key) (fn [io-options val]
                              (set! (.-transports io-options) (into-array val)))))

(defn set-io-options [io-options opts]
  "Sets the option vals on an IO$Options obj.
  opts should be a map of option keys and fns that take two params
  an options key (the name of the property) and the value of the property"
  (doall
    (map (fn [[k val]]
           (let [opt-fn (opt-fn k)]
             (opt-fn io-options val))) opts)))

(defn make-socket
  "Make a new socket.  event-map is a map of event names (strings) to Listener instances. path will be used as underlying IO.Options.path"
  ([url opts event-map]
   (let [connect-promise (promise)
         io-options (new io.socket.client.IO$Options)
         set-opts (set-io-options io-options opts)
         socket (IO/socket url io-options)
         effective-event-map (merge default-event-map
                                    {Socket/EVENT_CONNECT (fn [& args]
                                                            (debug (format "SocketIO client: Connected to %s" url))
                                                            (deliver connect-promise true))}
                                    event-map)]
     (doseq [e (keys effective-event-map)]
       (doto socket
         (.on e (->Listener (get effective-event-map e)))))
     (connect! socket)
     @connect-promise
     socket
     ))

  ([url event-map] (make-socket url nil event-map)))

(defn- make-args
  [msg hash]
  (cond (or (list? msg) (vector? msg) (seq? msg)) msg
        (map? msg) (let [json (JSONObject.)]
                     (debug (format "hash: %s" hash))
                     (debug (with-out-str (clojure.pprint/pprint msg)) )
                     (when hash (.put json "hash" hash))
                     (doseq [[k v] msg]
                       (.put json (name k) v))
                     [json])
        :else [msg]))

(defn emit!
  ([socket event msg hash]
   (let [args (make-args msg hash)]
     (.emit socket event (into-array Object args))
     hash))
  ([socket event msg]
   (emit! socket event msg nil)) )

(def pending-requests (atom {}))

(defn make-pass-take-socket
  [url]
  {:pre [(not-empty url)]}
  (let [socket (make-socket url {"take" (fn [data & rest]
                                          (let [data (cheshire.core/parse-string (.toString data) true)
                                                output (:output data)
                                                hash (:hash data)
                                                p (get @pending-requests hash)]
                                            (debug (with-out-str (clojure.pprint/pprint data)))
                                            (debug (format "take callback; hash: %s; p: %s" hash p))
                                            (when p
                                              (swap! pending-requests dissoc hash)
                                              (try (deliver p output)
                                                   (catch Exception e
                                                     (error (.getMessage e)))))))})]
    (emit! socket "join" (.id socket))
    socket))

(defn pass-take
  [socket msg]
  {:pre [(map? msg) (.connected socket)]}
  (let [p (promise)
        hash (.toString (java.util.UUID/randomUUID))
        f (future
            (Thread/sleep 60000)
            (when (not (realized? p))
              (swap! pending-requests dissoc hash)
              (deliver p :timeout)))]
    (swap! pending-requests assoc hash p)
    (emit! socket "pass"
           (assoc msg :from (.id socket))
           hash)
    p))
