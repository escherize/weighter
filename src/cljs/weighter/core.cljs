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

(def schema {})

(defn new-entity! [conn varmap]
  ((:tempids (d/transact! conn [(merge varmap {:db/id -1})])) -1))

(defonce tempid (let [n (atom 0)] (fn [] (swap! n dec))))

(defn populate! [conn]
  (d/transact!
   conn
   [{:db/id (tempid)
     :item/title "House Size"
     :item/value 5
     :item/weight 5}
    {:db/id (tempid)
     :item/title "House Location"
     :item/value 5
     :item/weight 10}]))

(defonce conn (let [conn (d/create-conn schema)]
                (populate! conn)
                (p/posh! conn)
                conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; subs

(defn item-ids [conn]
  (p/q '[:find ?eid
         :in $
         :where [?eid :item/title _]] conn))

(defn pull-item [conn id]
  (p/pull conn '[:item/title
                 :item/value
                 :item/weight] id))

(defn total [conn]
  (p/q '[:find ?weight ?value ?eid
         :in $
         :where
         [?eid :item/weight ?weight]
         [?eid :item/value ?value]] conn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn total-view [conn]
  (let [total (total conn)]
    (fn [conn]
      [:h1 (apply + (for [[weight value _] @total]
                       (* (/ weight 100) value)))])))

(defn display-item [conn id]
  (let [item (pull-item conn id)]
    (fn [conn]
      (let [{:keys [item/title item/value item/weight db/id]} @item]
        [:div {:style {:display "flex"
                       :flex-flow "column wrap"
                       :border "2px solid black"
                       :border-radius "3px"
                       :padding "10px"
                       :margin "10px"}}
         [:div {:style {:font-size "20px"}} title]
         [:div {:style {:flex-flow "row nowrap"}}
          [:div
           [:div [:span {:style {:display "inline-block" :width "100px"}} "Value "]
            [:input {:type :range :min 0 :max 10 :step 0.5 :value value
                     :on-change (fn [e]
                                  (p/transact!
                                   conn [[:db/add id
                                          :item/value (-> e .-target .-value)]]))}]
            [:span value]]]
          [:div
           [:div [:span {:style {:display "inline-block" :width "100px"}} "Weight "]
            [:input {:type :range :min 0 :max 10 :step 0.5 :value weight
                     :on-change (fn [e]
                                  (p/transact!
                                   conn [[:db/add id
                                          :item/weight (-> e .-target .-value)]]))}]
            [:span weight]]]]]))))

(defn page [conn]
  (let [item-ids (item-ids conn)]
    (fn [conn]
      [:div {:style {:display "flexbox" :flex-flow "column nowrap"}}
       [total-view conn]
       (into [:div {:style {:display "flexbox" :flex-flow "row wrap"}}]
             (for [[item-id] @item-ids]
               [display-item conn item-id]))
       [:input {:type "button"
                :on-click #(d/transact!
                            conn
                            [{:db/id (tempid)
                              :item/title "XXX"
                              :item/value 5
                              :item/weight 5}])
                :value "+"}]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn reload []
  (reagent/render [page conn]
                  (.getElementById js/document "app")))

(defn dev-setup []
  (when debug?
    (enable-console-print!)
    (rf/enable-frisk!)
    (rf/add-data :db (pr-str @conn))
    (println "dev mode")
    (devtools/install!)))

(defn ^:export main []
  (dev-setup)
  (reload))
