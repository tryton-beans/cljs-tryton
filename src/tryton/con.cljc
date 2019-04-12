(ns tryton.con
  (:require [ajax.core :refer [GET POST]]
            [clojure.data.codec.base64 :as b64]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]))

(defn string-to-base64-string [original]
  (String. (b64/encode (.getBytes original)) "UTF-8"))

(defn add-auth [session]
  (assoc session :auth
         (str "Session "
              (string-to-base64-string
               (clojure.string/join ":" ((juxt :login :userid :session) session))))))

(defn create-session [url db login response]
  (add-auth
   {:uri (str url "/" db "/")
    :login login
    :userid (first (:result response))
    :session (second (:result response))}))

(defn login [url db login pass lang]
  "Login tryton 5+. Returns channel"
  (let [out (chan)]
    (POST (str url "/" db "/")
          {:params
           {:method "common.db.login"
            :params [login {"password" pass} lang]
            :id 0}
           :handler (fn [res]
                      (go (>! out (create-session url db login res))
                          (close! out)))
           :error-handler (fn [{:keys [status status-text]}]
                            (go
                              (>! out
                                  {:error-status status
                                   :error-text status-text
                                   :result nil})
                              (close! out)))
           :format :json
           :response-format :json
           :keywords? true
           })
    out))

(defn login4 [url db login pass]
  "Login tryton 4.0. Returns channel"
  (let [out (chan)]
    (POST (str url "/" db "/")
          {:params
           {:method "common.db.login"
            :params [login pass]
            :id 0}
           :handler (fn [res]
                      (go (>! out (create-session url db login res))
                          (close! out)))
           :error-handler (fn [{:keys [status status-text]}]
                            (go
                              (>! out
                                  {:error-status status
                                   :error-text status-text
                                   :result nil})
                              (close! out)))
           :format :json
           :response-format :json
           :keywords? true
           })
    out))

(defn call [session method params]
  "json rpc call. Return channel"
  (let [out (chan)]
    (POST (:uri session)
          {:headers
           {"Authorization"
            (:auth session)
            }
           :params
           {:method method
            :params (conj params (get session :context {}))
            :id 1} ;; id ++
           :handler (fn [res]
                      (go
                        (>! out res)
                        (close! out)
                        ))
           :error-handler (fn [{:keys [status status-text]}]
                            (go
                              (>! out
                                  {:error-status status
                                   :error-text status-text
                                   :result nil})
                              (close! out)))
           :format :json
           :response-format :json
           :keywords? true
           })
    out))

(defn model-fields [session model-name]
  "Calls model.model-name.fields_get. Returns channel"
  (call session (str "model." model-name ".fields_get") []))

(defn model-read
  ([session model-name ids]
   (let [out (chan)]
     (go
       (>! out (<!
                (model-read session model-name ids
                            (->>
                             (<! (model-fields session model-name))
                             :result
                             keys
                             (map #(apply str (drop 1 (str %))))))))
       (close! out))
     out))
  ([session model-name ids fields]
   (call session
          (str "model." model-name ".read") [ids (conj (conj (set fields) "_timestamp") "id")])))

(defn model-search
  ([session model-name]
   (model-search session model-name [] 0 1000))
  ([session model-name domain]
   (model-search session model-name domain 0 1000))
  ([session model-name domain offset limit]
   (call session (str "model." model-name ".search") [domain offset limit nil])))

(defn model-create
  [session model-name fields]
  (call session (str "model." model-name ".create") [fields]))

(defn model-delete
  [session model-name ids]
  (call session (str "model." model-name ".delete") [ids]))

;; it requires the original model read and the updates done
(defn model-write
  [session model-name models write]
  (let [timestamps
        (->>
         models
         (map (fn [x] [(str model-name "," (:id x))
                       (:_timestamp x)]))
         flatten
         (apply hash-map)
         )
        ids (map :id models)
        ]
    (call (assoc session
                  :_timestamp timestamps) (str "model." model-name ".write") [ids write])))

(defn model-default
  [session model-name fields]
  (call session (str "model." model-name ".default_get") [fields]))
