(ns tryton.con-test
  (:require [clojure.test :refer [testing is deftest]]
            [clojure.core.async
             :as a
             :refer [<!!]]
            [tryton.con :refer [login model-fields
                                model-read model-search
                                model-create model-write
                                model-delete model-search-count]]))

(def tryton-url "http://demo5.0.tryton.org")
(def tryton-db "demo5.0")

(defn login-demo-user []
  (<!! (login tryton-url tryton-db "demo" "demo" "en"))
  )

(deftest login-test
  (testing "login succesfull"
    (is (= "demo"
           (:login
            (<!! (login tryton-url tryton-db "demo" "demo" "en"))))))
  (testing "login error"
    (is (= 401
           (:error-status
            (<!! (login tryton-url tryton-db "demo" "wrong password" "en")))))))

(deftest model-fields-test
  (testing "get fields model"
    (let [session (login-demo-user)]
      (is (= :account_payable
           (->>(<!!
                (model-fields
                 session
                 "party.party"
                 )
                )
               :result
               keys
               sort
               first))))))

(deftest model-read-test
  (testing "read all fields"
    (let [session (login-demo-user)]
      (is (=  "datetime"
              (->>
               (<!! (model-read session "party.party" [1]))
               :result
               first
               :create_date
               :__class__)))
      ))
  (testing "read a field ; _timestamp and id are always read"
    (let [session (login-demo-user)]
      (is (=  #{:id :create_date :_timestamp}
              (->>
               (<!! (model-read session "party.party" [1] ["create_date"]))
               :result
               first
               keys
               set
               ))))))

(deftest model-search-test
  (let [session (login-demo-user)]
    (testing "load all search"
      (is (= 1
             (->>
              (<!! (model-search session "party.party"))
              :result
              sort
              first))))

    (testing "load search one"
      (is (= 1
             (->>
              (<!! (model-search session "party.party" [["id" "=" "1"]]))
              :result
              count))))
    ))


(deftest crud-test
  (let [session (login-demo-user)]
    (testing "crud"
      ;; create a party
      (let [
            i-count (:result (<!! (model-search-count session "party.party")))
            id
            (->>
             (<!! (model-create session "party.party" [{:code "test-cljs-tryton"}]))
             :result
             first)
            party
            (->> (<!! (model-read session "party.party" [id]))
                 :result
                 first)
            written (<!! (model-write session "party.party" [party] {:name "update name"}))
            party_after
            (->> (<!! (model-read session "party.party" [id]))
                 :result
                 first)
            delete (<!! (model-delete session "party.party" [id]))
            f-count (:result (<!! (model-search-count session "party.party")))
            ]
        (is (= f-count i-count))
        (is (= "update name" (:name party_after)))
        (is (nil? (:result written)))
        (is (nil? (:result delete)))))
    ))

