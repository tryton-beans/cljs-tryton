(ns tryton.sync)

(defn add-domain-ts [domain time]
  ["AND"
   domain
   ["create_date" ">" time]
   ["write_date" ">" time]
   ]
  )

(comment
  (def t 
    (-> (m-fetch
         {:model "cargo.package"
          :domain  ["current_warehouse" "=" 61]
          :timestamp-next nil
          :fields ["id" "identifier" "_timestamp"]
          :last-data []
          :last-adds []
          :last-updates []
          })
        last
        :_timestamp
        #_(clojure.string/split #"\.")
        (clojure.string/replace "." "")
        #_first
        
        Long/parseLong
        (/ 100)
        java-time/instant
        (java-time/local-date-time "UTC")
        tryton.con/datetime->tryton
        ))

  (add-domain-ts [] t) 
  
  t
  (.toEpochMilli t)
  
  (java-time/local-date-time t "UTC")
  (java-time/as  (java-time/local-date-time) :year)
  
  (m-fetch
            {:model "cargo.package"
             :domain  (add-domain-ts ["AND" ["current_warehouse" "=" 61]
                                      ["shipment.closed" "=" false]]
                                     t)
             :timestamp-next nil
             :fields ["id" "identifier" "_timestamp" "shipment.closed" ]
             :last-data []
             :last-adds []
             :last-updates []
             })

  true


  

  
  (tryton.con/datetime->tryton
   0
   )
  
  (System/currentTimeMillis)

  (mount.core/start)
  tryton.session/session

  
  )

(defn m-fetch [data]
  (tryton.session/m-search-read (:model data)
                                (:domain data)
                                0
                                1000
                                
                                (:fields data))
  )
