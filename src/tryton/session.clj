(ns tryton.session
  (:require [tryton.con :refer [login4
                                login
                                model-create
                                model-delete
                                model-read
                                model-search
                                model-search-read
                                model-search-count
                                model-write
                                model-default
                                model-fields
                                call
                                doe
                                roe]]
            [mount.core :refer [defstate]]
            [config.core :refer [load-env]]
            [clojure.core.async :refer [<!!]]
            ))

(defn load-conf []
  (:tryton (load-env))
  )

(defn new-session []
  (let [conf (load-conf)]
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

(defn m-call [model method params]
  (doe (call session (str "model." model "." method) params)))

(defn m-call-models [model-name method models]
  (doe (call (assoc session
                    :_timestamp (tryton.con/get-timestamps-models model-name models))
             (str "model." model-name "." method) [(map :id models)])))

(defn m-search
  ([model search]
   (roe (model-search session model search)))
  ([model search offset limit]
   (roe (model-search session model search offset limit)))
  ([model search offset limit order]
   (roe (model-search session model search offset limit order))))

(defn m-search-read
  ([model search]
   (roe (model-search-read session model search)))
  ([model search offset limit]
   (roe (model-search-read session model search offset limit)))
  ([model search offset limit order]
   (roe (model-search-read session model search offset limit order)))
  ([model search offset limit order fields]
   (roe (model-search-read session model search offset limit order fields))))

(defn m-search-read-one
  "return [] if none found. Or a [found-entity]" 
  ([model search]
   (roe (model-search-read session model search 0 1 [])))
  ([model search fields]
   (roe (model-search-read session model search 0 1 [] fields))))

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

(defn m-fields [model]
  (roe (model-fields session model)))

(defn get-access [field-name]
  (let [parts
        (clojure.string/split (name field-name) #"\.")]
    (if (= 1 (count parts))
      [(keyword (first parts))]
      (->> parts
           (map #(str % "."))
           (drop-last)
           vec
           (#(conj % (last parts)))
           (mapv keyword)
           ))))

(defn get-t [t-map field]
  (get-in t-map (get-access field)))


(defn employee-user-company [company-id]
  (first
   (m-search "company.employee" [["AND"
                                  ["company" "=" company-id]
                                  ["id" "in"
                                   (:employees
                                    (first
                                     (m-read "res.user"
                                             [(:userid  session)]
                                             ["employees"])))                      
                                   ]]
                                 ])))

(defn set-preference-company-id
  ([company-id]
   (when (not (= company-id (:company @(:context session))))
     (set-preference-company-id
      company-id (employee-user-company company-id))))
  ([company-id employee-id]
   (when (not (= [company-id employee-id] ((juxt :company :employee) @(:context session))))
     (reset! (:context session) {:company company-id :employee employee-id})
     (<!! (tryton.con/call session
                           "model.res.user.set_preferences"
                           [{:company company-id :employee employee-id}]))))
  )
