(ns tryton.con
  (:require [ajax.core :refer [GET POST]]
            #?(:clj [clojure.data.codec.base64 :as b64]
               :cljs [goog.crypt.base64 :as js-b64])
            #?(:clj [java-time :as jt])
            [clojure.core.async
             :refer [>! <! go chan buffer close!
                     timeout
                     #?(:clj <!!)
                     ] :as a]))

(defn string-to-base64-string [original]
  #?(:clj (String. (b64/encode (.getBytes original)) "UTF-8")
     :cljs (js-b64/encodeString original))
  )

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
    :session (second (:result response))
    :context #?(:clj (atom {})
                :cljs {})
    }
   ))

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
            :params #?(:clj (conj params @(get session :context))
                       :cljs (conj params (get session :context)))
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
   (if (> (count fields) 0)
     (call session
           (str "model." model-name ".read") [ids (conj (conj (set fields) "_timestamp") "id")])
     (model-read session model-name ids))
   ))

(defn model-search
  ([session model-name]
   (model-search session model-name [] 0 1000))
  ([session model-name domain]
   (model-search session model-name domain 0 1000))
  ([session model-name domain offset limit]
   (call session (str "model." model-name ".search") [domain offset limit nil]))
  ([session model-name domain offset limit order]
   (call session (str "model." model-name ".search") [domain offset limit order])))

(defn model-search-read
  ([session model-name]
   (model-search-read session model-name [] 0 1000))
  ([session model-name domain]
   (model-search-read session model-name domain 0 1000))
  ([session model-name domain offset limit]
   (call session (str "model." model-name ".search_read")
         [domain offset limit nil]))
  ([session model-name domain offset limit order]
   (call session (str "model." model-name ".search_read")
         [domain offset limit order]))
  ([session model-name domain offset limit order fields]
   (call session (str "model." model-name ".search_read")
         [domain offset limit order fields])))


(defn model-search-count
  ([session model-name]
   (model-search-count session model-name []))
  ([session model-name domain]
   (call session (str "model." model-name ".search_count") [domain])))

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


#?(:clj
   (defn datetime->tryton
     "return a map ready to send to tryton with the datetime value"
     [datetime]
     {:__class__ "datetime"
      :year (jt/as datetime :year)
      :month (jt/as datetime :month-of-year)
      :day (jt/as datetime :day-of-month)
      :hour (jt/as datetime :hour-of-day)
      :minute (jt/as datetime :minute-of-hour)
      :second (jt/as datetime :second-of-minute)
      :microsecond (jt/as datetime :micro-of-second)
      }))

#?(:clj
   (defn time->tryton
     "return a map ready to send to tryton with the time value"
     [sql-timestamp]
     (when (not (nil? sql-timestamp))
       (let [time (jt/local-date-time sql-timestamp)]
         {:__class__ "time"
          :hour (jt/as time :hour-of-day)
          :minute (jt/as time :minute-of-hour)
          :second (jt/as time :second-of-minute)
          :microsecond (jt/as time :micro-of-second)
          }))))

#?(:clj
   (defn date->tryton
     "return a map ready to send to tryton with the time value"
     [sql-timestamp]
     (when (not (nil? sql-timestamp))
       (let [datetime (jt/local-date-time sql-timestamp)]
         {:__class__ "date"
          :year (jt/as datetime :year)
          :month (jt/as datetime :month-of-year)
          :day (jt/as datetime :day-of-month)       
          }))))

#?(:clj
   (defn decimal->tryton [decimal]
     {:__class__ "Decimal" :decimal (format "%.2f" decimal)}))


(defn doe [chan]
  "data or exception"
  #?(:clj
     (let [ret (<!! chan)]
       (if (contains? ret :error)
         (throw (Exception. (str (:error-status ret) ":" (:error-text ret) "-" (:error ret))))
         ret
         ))
     :cljs chan
     ))

(defn roe [chan]
  "result or exception"
  #?(:clj
     (let [ret (<!! chan)]
       (if (and (contains? ret :result) (not (nil? (:result ret))))
         (:result ret)
         (throw (Exception. (str (:error-status ret) ":" (:error-text ret) "-" (:error ret))))))
     :cljs chan)
  )
