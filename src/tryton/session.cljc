(ns tryton.session
  (:require [tryton.con :refer [login4
                                login
                                model-create
                                model-delete
                                model-read
                                model-search
                                model-search-count
                                model-write
                                model-default
                                doe
                                roe]]
            
            [mount.core :refer [defstate]]
            [config.core :refer [load-env]]
            [clojure.core.async :refer [<!!]]
            ))

(defn new-session []
  (let [conf (load-env)]
    (<!! (if (= "4" (:version conf))
           (login4
            (:url conf)
            (:db conf)
            (:user conf)
            (:password conf)
            )
           (login
            (:url conf)
            (:db conf)
            (:user conf)
            (:password conf)
            "en"
            )))))

(defstate session :start (new-session))

(defn set-preference-company-id [company-id]
  (<!! (tryton.con/call session "model.res.user.set_preferences" [{:company company-id}])))

(defn m-search
  ([model search]
   (roe (model-search session model search)))
  ([model search offset limit]
   (roe (model-search session model search offset limit)))
  ([model search offset limit order]
   (roe (model-search session model search offset limit order))))

(defn m-search-count
  ([model]
   (roe (model-search-count session model)))
  ([model search]
   (roe (model-search-count session model search))))

(defn m-read
  ([model ids]
   (roe (model-read session model ids)))
  ([model ids fields]
   (roe (model-read session model ids fields))))

(defn m-create [model models]
  (roe (model-create session model models)))

(defn m-delete [model ids]
  (doe (model-delete session model ids)))

(defn m-delete-search [model search]
  (doe (m-delete model (m-search model search))))

(defn m-write [model models write]
  (doe (model-write session model models write)))

(defn m-default [model fields]
  (roe (model-default session model fields)))
