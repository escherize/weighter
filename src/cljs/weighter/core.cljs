(ns weighter.core
  (:require
   [reagent.core :as reagent]
   [re-frisk.core :as rf]
   [devtools.core :as devtools]
   [datascript.core :as d]
   [posh.reagent :as p]
   [reagent.core :as r]))

(defonce debug? ^boolean js/goog.DEBUG)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DB

(def schema {:task/category         {:db/valueType :db.type/ref}
             :category/todo         {:db/valueType :db.type/ref}
             :todo/display-category {:db/valueType :db.type/ref}
             :task/name             {:db/unique :db.unique/identity}
             :todo/name             {:db/unique :db.unique/identity}
             :action/editing        {:db/cardinality :db.cardinality/many}})

(defn new-entity! [conn varmap]
  ((:tempids (d/transact! conn [(merge varmap {:db/id -1})])) -1))

(defonce tempid (let [n (atom 0)] (fn [] (swap! n dec))))

(defn populate! [conn]
  (let [todo-id    (new-entity! conn {:todo/name "Matt's List" :todo/listing :all})
        at-home    (new-entity! conn {:category/name "At Home" :category/todo todo-id})
        work-stuff (new-entity! conn {:category/name "Work Stuff" :category/todo todo-id})
        hobby      (new-entity! conn {:category/name "Hobby" :category/todo todo-id})]
    (d/transact!
     conn
     [{:db/id (tempid) :task/name "Clean Dishes" :task/done true :task/category at-home}
      {:db/id (tempid) :task/name "Mop Floors" :task/done true :task/pinned true :task/category at-home}
      {:db/id (tempid) :task/name "Draw a picture of a cat" :task/done false :task/category hobby}
      {:db/id (tempid) :task/name "Compose opera" :task/done true :task/category hobby}
      {:db/id (tempid) :task/name "stock market library"
       :task/done false :task/pinned true :task/category work-stuff}])))

(defonce app-state (let [db (d/create-conn schema)]
                     (populate! db)
                     (p/posh! db)
                     db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; subs

(defn todos [conn]
  (p/q '[:find ?tid ?name ?done ?category
         :in $
         :where
         [?tid :task/name ?name]
         [?tid :task/done ?done]
         [?tid :task/category ?cid]
         [?cid :category/name ?category]] conn))

(defn done-todos [conn]
  (p/q '[:find ?tid ?name ?done ?category
         :in $
         :where [?tid :task/name ?name]
         [?tid :task/done true]
         [?tid :task/done ?done]
         [?tid :task/category ?cid]
         [?cid :category/name ?category]] conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; subs

(defn listing [conn]
  (p/q '[:find ?listing
         :in $
         :where
         [_ :todo/listing ?listing]]
       conn))

(defn page [conn]
  (let [listing (listing conn)
        todos (todos conn)
        done-todos (done-todos conn)
        ]
    (fn [conn]
      [:div
       [:pre (pr-str (ffirst @listing))]
       ;;[:h1 [:pre (pr-str conn)]]
       ;;[:pre (pr-str @(p/q '[:find [?e] :in $ :where [?e _ _]] conn))]
       [:div {:style {:border "1px black solid"
                      :border-radius "2px"
                      :margin "10px"
                      :padding "10px"}}
        [:h3 "All Todos"]
        (into [:div]
              (for [[id name done category] @todos]
                [:div
                 [:input {:type :checkbox
                          :default-checked done
                          :on-click #(p/transact!
                                      conn [[:db/add id
                                             :task/done (not done)]])}]
                 name " (" category ")"]))]
       [:div {:style {:border "1px black solid"
                      :border-radius "2px"
                      :margin "10px"
                      :padding "10px"}}
        [:h3 "Done Todos"]
        (into [:div]
              (for [[id name] @done-todos]
                [:div name]))]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn reload []
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn dev-setup []
  (when debug?
    (enable-console-print!)
    (rf/enable-frisk!)
    (rf/add-data :db (pr-str @app-state))
    (rf/add-data :todos (pr-str @(todos app-state)))
    (println "dev mode")
    (devtools/install!)))

(defn ^:export main []
  (dev-setup)
  (reload))


(rf/add-data :todos (pr-str @(todos app-state)))
