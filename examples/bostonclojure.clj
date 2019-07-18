(ns bostonclj.examples
  (:require [clojure.string :as cljstr]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.pprint :as pp :refer [pprint]]

            [aerial.fs :as fs]
            [aerial.utils.string :as str]
            [aerial.utils.io :refer [letio] :as io]
            [aerial.utils.coll :refer [vfold] :as coll]
            [aerial.utils.math :as m]
            [aerial.utils.math.probs-stats :as p]
            [aerial.utils.math.infoth :as it]

            [aerial.bio.utils.files :as bufiles]
            [aerial.bio.utils.aligners :as aln]

            [aerial.hanami.common :as hc :refer [RMV]]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.core :as hmi]

            [aerial.saite.core :as saite]))

(saite/start 3000)



;;; The ubiquitous simple car scatter plot via template
(-> (hc/xform
     ht/point-chart
     :UDATA "data/cars.json"
     :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
    hmi/sv!)

;;; Another simple one right from IDL examples
(->
 (hc/xform
  ht/bar-chart
  :UDATA "data/seattle-weather.csv" :TOOLTIP RMV
  :X "date" :XTYPE "ordinal" :XUNIT "month"
  :Y "precipitation" :YAGG "mean")
 hmi/sv!)


;;; Simple barchart with slider instrument
;;;
(->>
 (let [data [{:a "A", :b 28 },
             {:a "B", :b 55 },
             {:a "C", :b 43 },
             {:a "D", :b 91 },
             {:a "E", :b 81 },
             {:a "F", :b 53 },
             {:a "G", :b 19 },
             {:a "H", :b 87 },
             {:a "I", :b 52 }]
       min -10.0
       minstr (-> min str (cljstr/split #"\.") first)
       max 10.0
       maxstr (-> max str (cljstr/split #"\.") first (#(str "+" %)))
       bottom `[[gap :size "50px"]
                [v-box
                 :children
                 [[p "some text to test, default 14px"]
                  [p {:style {:font-size "16px"}}
                   "some tex \\(f(x) = x^2\\), 16px"]
                  [p {:style {:font-size "18px"}}
                   "\\(f(x) = \\sqrt x\\), 18px"]
                  [p {:style {:font-size "20px"}}
                   "\\(ax^2 + bx + c = 0\\), 20px"]]]]]
   (hc/xform ht/bar-chart
             :USERDATA
             (merge
              (hc/get-default :USERDATA)
              {:slider `[[gap :size "10px"] [label :label "Add Bar"]
                         [label :label ~minstr]
                         [slider
                          :model :m1
                          :min ~min, :max ~max, :step 1.0
                          :width "200px"
                          :on-change :oc1]
                         [label :label ~maxstr]
                         [input-text
                          :model :m1
                          :width "60px", :height "26px"
                          :on-change :oc2]]})
             :HEIGHT 300, :WIDTH 350
              :VID :bc1 ;:BOTTOM bottom
             :X "a" :XTYPE "ordinal" :XTITLE "Foo" :Y "b" :YTITLE "Bar"
             :DATA data))
 hmi/sv!)




;;; Overview + Detail
;;; (hc/update-defaults :WIDTH 480 :HEIGHT 200)
;;; (hc/update-defaults :HEIGHT 400 :WIDTH 450)
(->>
 (hc/xform
  ht/vconcat-chart
  :UDATA "data/sp500.csv",
  :VCONCAT [(hc/xform ht/area-chart
             :X :date, :XTYPE :temporal, :XSCALE {:domain {:selection :brush}}
             :Y :price)
            (hc/xform ht/area-chart
             :HEIGHT 60,
             :SELECTION {:brush {:type :interval, :encodings [:x]}},
             :X :date :XTYPE :temporal
             :Y :price, :YAXIS {:tickCount 3, :grid false})])
 hmi/sv!)




;;; Area Chart
(-> (hc/xform
     ht/area-chart
     :UDATA  "data/unemployment-across-industries.json"
     :TOOLTIP RMV
     :X :date, :XTYPE :temporal, :XUNIT :yearmonth, :XFORMAT "%Y"
     :Y "count" :AGG "sum"
     :COLOR {:field "series", :type "nominal",
             :scale {:scheme "category20b"}})
    hmi/sv!)





;;; Grid example
;;; (hc/get-default :TOOLTIP)
;;; (hc/xform {:tt :TOOLTIP})
;;; (hc/update-defaults :HEIGHT 200 :WIDTH 250)
;;; (hc/update-defaults :HEIGHT 400 :WIDTH 450)

(->>
 (mapv #(apply hc/xform %)
       [[ht/point-chart
         :UDATA "data/cars.json" ; :TOPTS {:order :col :size "none"}
         :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin"]
        (let [data (->> (range 0.005 0.999 0.001)
                        (mapv (fn[p] {:x p, :y (- (m/log2 p)) :col "SI"})))]
          [ht/layer-chart
           :TITLE "Self Information (unexpectedness)"
           :LAYER [(hc/xform ht/xrule-layer :AGG "mean")
                   (hc/xform ht/line-layer
                     :XTITLE "Probability of event" :YTITLE "-log(p)")]
           :DATA data])
        [ht/bar-chart
         :UDATA "data/seattle-weather.csv" :TOOLTIP RMV
         :X "date" :XTYPE "ordinal" :XUNIT "month"
         :Y "precipitation" :YAGG "mean"]
        [ht/layer-chart
         :UDATA "data/seattle-weather.csv"
         :LAYER [(hc/xform ht/bar-layer
                   :TOOLTIP RMV
                   :X "date" :XTYPE "ordinal" :XUNIT "month"
                   :Y "precipitation" :YAGG "mean"
                   :SELECTION {:brush {:type "interval", :encodings ["x"]}}
                   :OPACITY {:condition {:selection "brush", :value 1},
                             :value 0.7})
                 (hc/xform ht/yrule-layer
                   :TRANSFORM [{:filter {:selection "brush"}}]
                   :Y "precipitation" :AGG "mean" :YRL-COLOR "firebrick")]]])
 hmi/sv!)




;;; Some Tree Layouts. Note the mode is Vega!
;;;
(->>
 [(hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "diagonal" :LAYOUT "tidy" :FONTSIZE 11
   :CFIELD "depth")
  (hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "line" :LAYOUT "tidy" :FONTSIZE 11
   :CFIELD "depth")
  (hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "orthogonal" :LAYOUT "cluster"
   :CFIELD "depth")
  (hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "curve" :LAYOUT "tidy"
   :CFIELD "depth")]
 hmi/sv!)




;;; Picture frames, hiccup, re-com, markdown, and LaTex
;;;

;;; With Mark Down and LaTex
(let [data (->> (range 0.001 100.0 0.1)
                (mapv #(do {:x (ac/roundit %)
                            :y (-> % Math/sqrt ac/roundit)})))]
  (->> (hc/xform ht/line-chart :VID :sqrt
        :TID :picframes
        :BOTTOM `[[gap :size "230px"]
                  [p {:style {:font-size "18px"}}
                   "\\(f(x) = \\sqrt x\\)"]]
        :RIGHT `[[gap :size "10px"]
                 [v-box
                  :children
                  [(md "#### The square root function")
                   (md "* \\\\(f(x) = \\\\sqrt x\\\\)")
                   (md "* _two_\n* **three**")]]]
        :DATA data) hmi/sv!))


;;; Each element on different chart
;;;
(let [_ (hc/add-defaults
         :FMNM #(-> :SIDE % name cljstr/capitalize)
         :STYLE hc/RMV)
      frame-template {:frame
                      {:SIDE `[[gap :size :GAP]
                               [p {:style :STYLE}
                                "This is the " [:span.bold :FMNM]
                                " 'board' of a picture "
                                [:span.italic.bold "frame."]]]}}
      frame-top (hc/xform
                 frame-template :SIDE :top :GAP "10px")

      frame-left (hc/xform
                  frame-template :SIDE :left :GAP "10px"
                  :STYLE {:width "100px" :min-width "50px"})
      frame-right (merge-with merge
                   (hc/xform
                    frame-template :SIDE :right :GAP "2px"
                    :STYLE {:width "100px" :min-width "50px"})
                   (hc/xform
                    frame-template :SIDE :left :GAP "2px"
                    :STYLE {:width "100px" :min-width "50px"
                            :color "white"}))
      frame-bot (hc/xform
                 frame-template :SIDE :bottom :GAP "10px")]
  (->> (mapv #(hc/xform ht/point-chart
                :HEIGHT 200 :WIDTH 250 :TID :picframes
                :USERDATA (merge (hc/get-default :USERDATA) %)
                :UDATA "data/cars.json"
                :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
             [frame-top frame-left frame-bot frame-right])
       hmi/sv!))


;;; Picture frame - 4 elements

(let [text "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."
      top `[[gap :size "150px"]
            [p "An example showing a "
             [:span.bold "picture "] [:span.italic.bold "frame"]
             ". This is the top 'board'"
             [:br] ~text]]
      left `[[gap :size "10px"]
             [p {:style {:width "100px" :min-width "50px"}}
              "Some text on the " [:span.bold "left:"] [:br] ~text]]
      right `[[gap :size "2px"]
              [p {:style {:width "200px" :min-width "50px"
                          :font-size "20px" :color "red"}}
               "Some large text on the " [:span.bold "right:"] [:br]
               ~(.substring text 0 180)]]
      bottom `[[gap :size "200px"]
               [title :level :level3
                :label [p {:style {:font-size "large"}}
                        "Some text on the "
                        [:span.bold "bottom"] [:br]
                        "With a cool info button "
                        [info-button
                         :position :right-center
                         :info
                         [:p "Check out Saite Visualizer!" [:br]
                          "Built with Hanami!" [:br]
                          [hyperlink-href
                           :label "Saite "
                           :href  "https://github.com/jsa-aerial/saite"
                           :target "_blank"]]]]]]]
  (->> [(hc/xform ht/point-chart
          :TID :picframes
          :TOP top :BOTTOM bottom :LEFT left :RIGHT right
          :UDATA "data/cars.json"
          :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")]
       hmi/sv!))


;;; Empty picture frame - 4 elements
;;;
(let [text "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."
      top `[[gap :size "50px"]
            [p {:style {:width "600px" :min-width "50px"}}
             "An example empty picture frame showing all four areas."
             " This is the " [:span.bold "top"] " area. "
             ~text ~text ~text]]
      left `[[gap :size "50px"]
             [p {:style {:width "300px" :min-width "50px"}}
              "The " [:span.bold "left "] "area as a column of text. "
              ~text ~text ~text ~text]]
      right `[[gap :size "70px"]
              [p {:style {:width "300px" :min-width "50px"}}
               "The " [:span.bold "right "] "area as a column of text. "
               ~text ~text ~text ~text]]
      bottom `[[gap :size "50px"]
               [v-box
                :children
                [[p {:style {:width "600px" :min-width "50px"}}
                  "The " [:span.bold "bottom "]
                  "area showing a variety of text. "
                  [:span.italic ~text] [:span.bold ~text]]
                 [p {:style {:width "400px" :min-width "50px"
                             :font-size "20px"}}
                  "some TeX: " "\\(f(x) = \\sqrt x\\)"]
                 [md {:style {:font-size "16px" :color "blue"}}
                  "#### Some Markup
* **Item 1** Lorem ipsum dolor sit amet, consectetur adipiscing elit.
* **Item 2** Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."]
                 [p {:style {:width "600px" :min-width "50px"
                             :color "red"}}
                  ~text]]]]]
  (->> (hc/xform ht/empty-chart
        :TID :picframes :TOP top :BOTTOM bottom :LEFT left :RIGHT right)
       hmi/sv!))


;;; With and without chart
;;;
(let [text "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."
      top `[[gap :size "50px"]
            [p "Here's a 'typical' chart/plot filled picture frame."
             "It only has the top area"
             [:br] ~text]]
      left `[[gap :size "20px"]
             [p {:style {:width "200px" :min-width "50px"}}
              "This is an empty frame with a " [:span.bold "left "]
              "column of text" [:br] ~text ~text ~text ~text]]
      right `[[gap :size "30px"]
              [p {:style {:width "200px" :min-width "50px"}}
               "And a " [:span.bold "right "]
               "column of text"
               [:br] ~text ~text ~text ~text]]]
  (->> [(hc/xform ht/point-chart
          :TID :picframes :UDATA "data/cars.json"
          :TOP top
          :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
        (hc/xform ht/empty-chart
          :TID :picframes
          :LEFT left :RIGHT right)]
       hmi/sv!))




;;; Rachel and Abhisheks tRNA experiment results - raw counts
;;;
;;; ***NOTE: this won't work for outside users as the data is not available!!
;;;
(->>
 (let [data (->> "~/Bio/Rachel-Abhishek/lib-sq-counts.clj"
                 fs/fullpath slurp read-string
                 (coll/dropv 3) ;5
                 (coll/takev 3) ;5
                 (mapcat #(->> % (sort-by :cnt >) (coll/takev 50))) vec)]
   (hc/xform ht/grouped-bar-chart
             :TID :dists :TOPTS {:order :row :size "auto"}
             :WIDTH (-> 550 (/ 9) double Math/round (- 15))
             :TITLE (format "Counts for %s"
                            (->> data first :nm (str/split #"-") first))
             :TOFFSET 40
             :DATA data
             :X "nm" :XTYPE "nominal" :XTITLE ""
             :Y "cnt" :YTITLE "Count"
             :COLOR ht/default-color
             :COLUMN "sq" :COLTYPE "nominal"
             ))
 hmi/sv!)
