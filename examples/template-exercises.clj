(ns template.exercises
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


(hc/update-defaults :SESSION-NAME "Saite Interview")


(hc/xform {:a :X})
(hc/get-default :X)

(hc/xform {:a :X} :X "foo")
(hc/xform {:a :X} :X {:y :Y})
(hc/get-default :Y)

(hc/xform {:a :Saite})
(hc/get-default :Saite)
(hc/xform {:a :Saite} :Saite 12)

(hc/xform {:a :FOO} :FOO #{1 2})
(hc/xform {:a :FOO} :FOO #{})

(hc/get-default :XSCALE)
(hc/xform {:a :FOO} :FOO {:scale :XSCALE})


(hc/get-default :DATA)
(hc/get-default :NDATA)
(hc/get-default :UDATA)

(hc/get-default :VALDATA)
((hc/get-default :VALDATA) {:DATA "one"})
((hc/get-default :VALDATA) {:UDATA "data/cars.json"})

ht/data-options
(hc/xform ht/data-options)
(hc/xform ht/data-options :DATA "one")
(hc/xform ht/data-options :UDATA "data/cars.json")

ht/view-base

(hc/get-default :ENCODING)
(hc/xform {:encoding :ENCODING})

(hc/get-default :USERDATA)
(hc/xform (hc/get-default :USERDATA))

(hc/get-default :TLBL)
;;:TLBL #(-> :TID % name cljstr/capitalize)
(hc/get-default :TID)
(hc/xform {:label :TLBL})


(hc/xform ht/view-base)
(hc/xform ht/view-base :UDATA "data/cars.json")

ht/point-chart

(hc/xform
 ht/point-chart
 :UDATA "data/cars.json"
 :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")




