(ns aerial.saite.miscdata
  (:require
   [clojure.string]
   [re-com.core]))

(def cljs-core-names
  ["*"
   "*1"
   "*2"
   "*3"
   "*assert*"
   "*clojurescript-version*"
   "*command-line-args*"
   "*e"
   "*eval*"
   "*exec-tap-fn*"
   "*flush-on-newline*"
   "*loaded-libs*"
   "*main-cli-fn*"
   "*ns*"
   "*out*"
   "*print-dup*"
   "*print-err-fn*"
   "*print-fn*"
   "*print-fn-bodies*"
   "*print-length*"
   "*print-level*"
   "*print-meta*"
   "*print-namespace-maps*"
   "*print-newline*"
   "*print-readably*"
   "*target*"
   "*unchecked-arrays*"
   "*unchecked-if*"
   "*warn-on-infer*"
   "+"
   "-"
   "->ArrayChunk"
   "->ArrayIter"
   "->ArrayList"
   "->ArrayNode"
   "->ArrayNodeIterator"
   "->ArrayNodeSeq"
   "->Atom"
   "->BitmapIndexedNode"
   "->BlackNode"
   "->Box"
   "->ChunkBuffer"
   "->ChunkedCons"
   "->ChunkedSeq"
   "->Cons"
   "->Cycle"
   "->Delay"
   "->ES6EntriesIterator"
   "->ES6Iterator"
   "->ES6IteratorSeq"
   "->ES6SetEntriesIterator"
   "->Eduction"
   "->Empty"
   "->EmptyList"
   "->HashCollisionNode"
   "->HashMapIter"
   "->HashSetIter"
   "->IndexedSeq"
   "->IndexedSeqIterator"
   "->Iterate"
   "->KeySeq"
   "->Keyword"
   "->LazySeq"
   "->List"
   "->Many"
   "->MapEntry"
   "->MetaFn"
   "->MultiFn"
   "->MultiIterator"
   "->Namespace"
   "->NeverEquiv"
   "->NodeIterator"
   "->NodeSeq"
   "->ObjMap"
   "->PersistentArrayMap"
   "->PersistentArrayMapIterator"
   "->PersistentArrayMapSeq"
   "->PersistentHashMap"
   "->PersistentHashSet"
   "->PersistentQueue"
   "->PersistentQueueIter"
   "->PersistentQueueSeq"
   "->PersistentTreeMap"
   "->PersistentTreeMapSeq"
   "->PersistentTreeSet"
   "->PersistentVector"
   "->RSeq"
   "->Range"
   "->RangeIterator"
   "->RangedIterator"
   "->RecordIter"
   "->RedNode"
   "->Reduced"
   "->Repeat"
   "->SeqIter"
   "->Single"
   "->StringBufferWriter"
   "->StringIter"
   "->Subvec"
   "->Symbol"
   "->TaggedLiteral"
   "->TransformerIterator"
   "->TransientArrayMap"
   "->TransientHashMap"
   "->TransientHashSet"
   "->TransientVector"
   "->UUID"
   "->ValSeq"
   "->Var"
   "->VectorNode"
   "->Volatile"
   "->t_cljs$core10997"
   "->t_cljs$core9458"
   "-add-method"
   "-add-watch"
   "-as-transient"
   "-assoc"
   "-assoc!"
   "-assoc-n"
   "-assoc-n!"
   "-chunked-first"
   "-chunked-next"
   "-chunked-rest"
   "-clj->js"
   "-clone"
   "-comparator"
   "-compare"
   "-conj"
   "-conj!"
   "-contains-key?"
   "-count"
   "-default-dispatch-val"
   "-deref"
   "-deref-with-timeout"
   "-disjoin"
   "-disjoin!"
   "-dispatch-fn"
   "-dissoc"
   "-dissoc!"
   "-drop-first"
   "-empty"
   "-entry-key"
   "-equiv"
   "-find"
   "-first"
   "-flush"
   "-get-method"
   "-hash"
   "-invoke"
   "-iterator"
   "-js->clj"
   "-key"
   "-key->js"
   "-kv-reduce"
   "-lookup"
   "-meta"
   "-methods"
   "-name"
   "-namespace"
   "-next"
   "-notify-watches"
   "-nth"
   "-peek"
   "-persistent!"
   "-pop"
   "-pop!"
   "-pr-writer"
   "-prefer-method"
   "-prefers"
   "-realized?"
   "-reduce"
   "-remove-method"
   "-remove-watch"
   "-reset"
   "-reset!"
   "-rest"
   "-rseq"
   "-seq"
   "-sorted-seq"
   "-sorted-seq-from"
   "-swap!"
   "-val"
   "-vreset!"
   "-with-meta"
   "-write"
   "/"
   "<"
   "<="
   "="
   "=="
   ">"
   ">="
   "APersistentVector"
   "ASeq"
   "ArrayChunk"
   "ArrayIter"
   "ArrayList"
   "ArrayNode"
   "ArrayNodeIterator"
   "ArrayNodeSeq"
   "Atom"
   "BitmapIndexedNode"
   "BlackNode"
   "Box"
   "CHAR_MAP"
   "ChunkBuffer"
   "ChunkedCons"
   "ChunkedSeq"
   "Cons"
   "Cycle"
   "DEMUNGE_MAP"
   "DEMUNGE_PATTERN"
   "Delay"
   "ES6EntriesIterator"
   "ES6Iterator"
   "ES6IteratorSeq"
   "ES6SetEntriesIterator"
   "Eduction"
   "Empty"
   "EmptyList"
   "ExceptionInfo"
   "Fn"
   "HashCollisionNode"
   "HashMapIter"
   "HashSetIter"
   "IAssociative"
   "IAtom"
   "IChunk"
   "IChunkedNext"
   "IChunkedSeq"
   "ICloneable"
   "ICollection"
   "IComparable"
   "ICounted"
   "IDeref"
   "IDerefWithTimeout"
   "IEditableCollection"
   "IEmptyableCollection"
   "IEncodeClojure"
   "IEncodeJS"
   "IEquiv"
   "IFind"
   "IFn"
   "IHash"
   "IIndexed"
   "IIterable"
   "IKVReduce"
   "IList"
   "ILookup"
   "IMap"
   "IMapEntry"
   "IMeta"
   "IMultiFn"
   "INIT"
   "INamed"
   "INext"
   "IPending"
   "IPrintWithWriter"
   "IRecord"
   "IReduce"
   "IReset"
   "IReversible"
   "ISeq"
   "ISeqable"
   "ISequential"
   "ISet"
   "ISorted"
   "IStack"
   "ISwap"
   "ITER_SYMBOL"
   "ITransientAssociative"
   "ITransientCollection"
   "ITransientMap"
   "ITransientSet"
   "ITransientVector"
   "IUUID"
   "IVector"
   "IVolatile"
   "IWatchable"
   "IWithMeta"
   "IWriter"
   "IndexedSeq"
   "IndexedSeqIterator"
   "Inst"
   "Iterate"
   "KeySeq"
   "Keyword"
   "LazySeq"
   "List"
   "MODULE_INFOS"
   "MODULE_URIS"
   "Many"
   "MapEntry"
   "MetaFn"
   "MultiFn"
   "MultiIterator"
   "NS_CACHE"
   "Namespace"
   "NeverEquiv"
   "NodeIterator"
   "NodeSeq"
   "ObjMap"
   "PROTOCOL_SENTINEL"
   "PersistentArrayMap"
   "PersistentArrayMapIterator"
   "PersistentArrayMapSeq"
   "PersistentHashMap"
   "PersistentHashSet"
   "PersistentQueue"
   "PersistentQueueIter"
   "PersistentQueueSeq"
   "PersistentTreeMap"
   "PersistentTreeMapSeq"
   "PersistentTreeSet"
   "PersistentVector"
   "RSeq"
   "Range"
   "RangeIterator"
   "RangedIterator"
   "RecordIter"
   "RedNode"
   "Reduced"
   "Repeat"
   "START"
   "SeqIter"
   "Single"
   "StringBufferWriter"
   "StringIter"
   "Subvec"
   "Symbol"
   "TaggedLiteral"
   "TransformerIterator"
   "TransientArrayMap"
   "TransientHashMap"
   "TransientHashSet"
   "TransientVector"
   "UUID"
   "ValSeq"
   "Var"
   "VectorNode"
   "Volatile"
   "aclone"
   "add-tap"
   "add-to-string-hash-cache"
   "add-watch"
   "aget"
   "alength"
   "alter-meta!"
   "ancestors"
   "any?"
   "apply"
   "apply-to"
   "array"
   "array-chunk"
   "array-index-of"
   "array-iter"
   "array-list"
   "array-map"
   "array-seq"
   "array?"
   "aset"
   "assoc"
   "assoc!"
   "assoc-in"
   "associative?"
   "atom"
   "bit-and"
   "bit-and-not"
   "bit-clear"
   "bit-count"
   "bit-flip"
   "bit-not"
   "bit-or"
   "bit-set"
   "bit-shift-left"
   "bit-shift-right"
   "bit-shift-right-zero-fill"
   "bit-test"
   "bit-xor"
   "boolean"
   "boolean?"
   "booleans"
   "bounded-count"
   "butlast"
   "byte"
   "bytes"
   "cat"
   "char"
   "char?"
   "chars"
   "chunk"
   "chunk-append"
   "chunk-buffer"
   "chunk-cons"
   "chunk-first"
   "chunk-next"
   "chunk-rest"
   "chunked-seq"
   "chunked-seq?"
   "clj->js"
   "clone"
   "cloneable?"
   "coll?"
   "comp"
   "comparator"
   "compare"
   "compare-and-set!"
   "complement"
   "completing"
   "concat"
   "conj"
   "conj!"
   "cons"
   "constantly"
   "contains?"
   "count"
   "counted?"
   "create-ns"
   "cycle"
   "dec"
   "dedupe"
   "default-dispatch-val"
   "def"
   "defn"
   "delay?"
   "demunge"
   "deref"
   "derive"
   "descendants"
   "disj"
   "disj!"
   "dispatch-fn"
   "dissoc"
   "dissoc!"
   "distinct"
   "distinct?"
   "divide"
   "doall"
   "dorun"
   "dotimes"
   "double"
   "double-array"
   "double?"
   "doubles"
   "drop"
   "drop-last"
   "drop-while"
   "eduction"
   "empty"
   "empty?"
   "enable-console-print!"
   "ensure-reduced"
   "equiv-map"
   "es6-entries-iterator"
   "es6-iterator"
   "es6-iterator-seq"
   "es6-set-entries-iterator"
   "eval"
   "even?"
   "every-pred"
   "every?"
   "ex-cause"
   "ex-data"
   "ex-info"
   "ex-message"
   "false?"
   "ffirst"
   "filter"
   "filterv"
   "find"
   "find-macros-ns"
   "find-ns"
   "find-ns-obj"
   "first"
   "flatten"
   "float"
   "float?"
   "floats"
   "flush"
   "fn?"
   "fnext"
   "fnil"
   "force"
   "frequencies"
   "gensym"
   "gensym_counter"
   "get"
   "get-in"
   "get-method"
   "get-validator"
   "group-by"
   "halt-when"
   "hash"
   "hash-combine"
   "hash-keyword"
   "hash-map"
   "hash-ordered-coll"
   "hash-set"
   "hash-string"
   "hash-string*"
   "hash-unordered-coll"
   "ident?"
   "identical?"
   "identity"
   "if"
   "ifind?"
   "ifn?"
   "imul"
   "inc"
   "indexed?"
   "infinite?"
   "inst-ms"
   "inst-ms*"
   "inst?"
   "instance?"
   "int"
   "int-array"
   "int-rotate-left"
   "int?"
   "integer?"
   "interleave"
   "interpose"
   "into"
   "into-array"
   "ints"
   "is_proto_"
   "isa?"
   "iter"
   "iterable?"
   "iterate"
   "js->clj"
   "js-delete"
   "js-invoke"
   "js-keys"
   "js-mod"
   "js-obj"
   "js-reserved"
   "juxt"
   "keep"
   "keep-indexed"
   "key"
   "key->js"
   "key-test"
   "keys"
   "keyword"
   "keyword-identical?"
   "keyword?"
   "last"
   "let"
   "letfn"
   "list"
   "list*"
   "list?"
   "load-file"
   "long"
   "long-array"
   "longs"
   "m3-C1"
   "m3-C2"
   "m3-fmix"
   "m3-hash-int"
   "m3-hash-unencoded-chars"
   "m3-mix-H1"
   "m3-mix-K1"
   "m3-seed"
   "make-array"
   "make-hierarchy"
   "map"
   "map-entry?"
   "map-indexed"
   "map?"
   "mapcat"
   "mapv"
   "max"
   "max-key"
   "memoize"
   "merge"
   "merge-with"
   "meta"
   "methods"
   "min"
   "min-key"
   "missing-protocol"
   "mix-collection-hash"
   "mk-bound-fn"
   "mod"
   "munge"
   "name"
   "namespace"
   "nat-int?"
   "native-satisfies?"
   "neg-int?"
   "neg?"
   "newline"
   "next"
   "nfirst"
   "nil-iter"
   "nil?"
   "nnext"
   "not"
   "not-any?"
   "not-empty"
   "not-every?"
   "not-native"
   "not="
   "ns-interns*"
   "ns-name"
   "nth"
   "nthnext"
   "nthrest"
   "number?"
   "obj-map"
   "object-array"
   "object?"
   "odd?"
   "parents"
   "partial"
   "partition"
   "partition-all"
   "partition-by"
   "peek"
   "persistent!"
   "persistent-array-map-seq"
   "pop"
   "pop!"
   "pos-int?"
   "pos?"
   "pr"
   "pr-seq-writer"
   "pr-sequential-writer"
   "pr-str"
   "pr-str*"
   "pr-str-with-opts"
   "prefer-method"
   "prefers"
   "prim-seq"
   "print"
   "print-map"
   "print-meta?"
   "print-prefix-map"
   "print-str"
   "println"
   "println-str"
   "prn"
   "prn-str"
   "prn-str-with-opts"
   "qualified-ident?"
   "qualified-keyword?"
   "qualified-symbol?"
   "quot"
   "rand"
   "rand-int"
   "rand-nth"
   "random-sample"
   "random-uuid"
   "range"
   "ranged-iterator"
   "re-find"
   "re-matches"
   "re-pattern"
   "re-seq"
   "realized?"
   "record?"
   "reduce"
   "reduce-kv"
   "reduceable?"
   "reduced"
   "reduced?"
   "reductions"
   "regexp?"
   "rem"
   "remove"
   "remove-all-methods"
   "remove-method"
   "remove-tap"
   "remove-watch"
   "repeat"
   "repeatedly"
   "replace"
   "replicate"
   "reset!"
   "reset-meta!"
   "reset-vals!"
   "rest"
   "reverse"
   "reversible?"
   "rseq"
   "rsubseq"
   "run!"
   "second"
   "select-keys"
   "seq"
   "seq-iter"
   "seq?"
   "seqable?"
   "sequence"
   "sequential?"
   "set"
   "set-from-indexed-seq"
   "set-print-err-fn!"
   "set-print-fn!"
   "set-validator!"
   "set?"
   "short"
   "shorts"
   "shuffle"
   "simple-ident?"
   "simple-keyword?"
   "simple-symbol?"
   "some"
   "some-fn"
   "some?"
   "sort"
   "sort-by"
   "sorted-map"
   "sorted-map-by"
   "sorted-set"
   "sorted-set-by"
   "sorted?"
   "special-symbol?"
   "split-at"
   "split-with"
   "spread"
   "str"
   "string-hash-cache"
   "string-hash-cache-count"
   "string-iter"
   "string-print"
   "string?"
   "subs"
   "subseq"
   "subvec"
   "swap!"
   "swap-vals!"
   "symbol"
   "symbol-identical?"
   "symbol?"
   "system-time"
   "t_cljs$core10997"
   "t_cljs$core9458"
   "tagged-literal"
   "tagged-literal?"
   "take"
   "take-last"
   "take-nth"
   "take-while"
   "tap>"
   "test"
   "to-array"
   "to-array-2d"
   "trampoline"
   "transduce"
   "transformer-iterator"
   "transient"
   "tree-seq"
   "true?"
   "truth_"
   "type"
   "type->str"
   "unchecked-add"
   "unchecked-add-int"
   "unchecked-byte"
   "unchecked-char"
   "unchecked-dec"
   "unchecked-dec-int"
   "unchecked-divide-int"
   "unchecked-double"
   "unchecked-float"
   "unchecked-inc"
   "unchecked-inc-int"
   "unchecked-int"
   "unchecked-long"
   "unchecked-multiply"
   "unchecked-multiply-int"
   "unchecked-negate"
   "unchecked-negate-int"
   "unchecked-remainder-int"
   "unchecked-short"
   "unchecked-subtract"
   "unchecked-subtract-int"
   "undefined?"
   "underive"
   "unreduced"
   "unsigned-bit-shift-right"
   "update"
   "update-in"
   "uri?"
   "uuid"
   "uuid?"
   "val"
   "vals"
   "var?"
   "vary-meta"
   "vec"
   "vector"
   "vector?"
   "volatile!"
   "volatile?" 
   "vreset!" 
   "with-meta"
   "when"
   "while"
   "write-all" 
   "zero?" 
   "zipmap"])


(def cljs-str-names
  (->> 'clojure.string ns-publics (mapv #(-> % first name)) sort
       (mapv #(str "str/" %))))

(def re-com-names
  (->> 're-com.core ns-publics (mapv #(-> % first name)) sort))

(def saite-core-names
  ["sc/add-ratom"
   "sc/add-symxlate"
   "sc/add-update-frame"
   "sc/bar-slider-fn"
   "sc/calc-dimensions"
   "sc/default-start-tab"
   "sc/delete-frame"
   "sc/frame-callback"
   "sc/get-cm-cb"
   "sc/get-cur-cm"
   "sc/get-cur-date"
   "sc/get-current-cm-frame-info"
   "sc/get-ratom"
   "sc/get-symxlate"
   "sc/instrumentor"
   "sc/mathjax-chan"
   "sc/mathjax-put"
   "sc/mdcm"
   "sc/newdoc-data"
   "sc/react-hack"
   "sc/read-data"
   "sc/run-prom-chain"
   "sc/saite-header"
   "sc/selfhost-jvm-eval"
   "sc/set-md-defaults"
   "sc/symxlate-callback" 
   "sc/update-data" 
   "sc/xform-tab-defaults"])

(def hanami-core-names
  ["hmi/active-tabs"
   "hmi/add-tab"
   "hmi/add-to-tab-body"
   "hmi/app-db"
   "hmi/app-send"
   "hmi/app-stop"
   "hmi/connect"
   "hmi/data-db"
   "hmi/dbg?"
   "hmi/dbgoff"
   "hmi/dbgon"
   "hmi/default-frame-cb"
   "hmi/default-header-fn"
   "hmi/default-instrumentor-fn"
   "hmi/default-opts"
   "hmi/del-tab"
   "hmi/del-vgviews"
   "hmi/empty-chart?"
   "hmi/frameit"
   "hmi/get-adb"
   "hmi/get-chan"
   "hmi/get-cur-tab"
   "hmi/get-ddb"
   "hmi/get-default-frame"
   "hmi/get-frame-elements"
   "hmi/get-last-tid"
   "hmi/get-tab-body"
   "hmi/get-tab-field"
   "hmi/get-vgview"
   "hmi/get-vgviews"
   "hmi/get-vspec"
   "hmi/get-ws"
   "hmi/hanami"
   "hmi/hanami-main"
   "hmi/header"
   "hmi/init-tabs"
   "hmi/make-frame"
   "hmi/make-instrumentor"
   "hmi/make-spec-frame-pairs"
   "hmi/make-tabdefs"
   "hmi/md"
   "hmi/merge-old-new-opts"
   "hmi/message-dispatch"
   "hmi/move-tab"
   "hmi/next-key"
   "hmi/on-msg"
   "hmi/on-open"
   "hmi/print-chan"
   "hmi/print-when"
   "hmi/printchan"
   "hmi/re-com-xref"
   "hmi/register"
   "hmi/remove-from-tab-body"
   "hmi/replace-tab"
   "hmi/send-msg"
   "hmi/session-input"
   "hmi/set-cur-tab"
   "hmi/set-dbg"
   "hmi/set-session-name"
   "hmi/start"
   "hmi/sv!"
   "hmi/tab-pos"
   "hmi/tabs"
   "hmi/update-adb"
   "hmi/update-cur-tab"
   "hmi/update-data"
   "hmi/update-ddb"
   "hmi/update-frame"
   "hmi/update-frame-element"
   "hmi/update-opts"
   "hmi/update-tab-field"
   "hmi/update-tabs"
   "hmi/update-vgviews"
   "hmi/update-vspecs"
   "hmi/user-msg"
   "hmi/vgl" 
   "hmi/vis-list" 
   "hmi/visualize" 
   "hmi/xform-recom"])

(def hanami-common-names
  ["hc/RMV"
   "hc/_defaults"
   "hc/add-defaults"
   "hc/color-key"
   "hc/data-key"
   "hc/default-opts"
   "hc/fdata-key"
   "hc/get-data-vals"
   "hc/get-default"
   "hc/get-defaults"
   "hc/opacity-key"
   "hc/reset-defaults"
   "hc/set-color-key!"
   "hc/set-data-key!"
   "hc/set-fdata-key!"
   "hc/set-opacity-key!"
   "hc/set-shape-key!"
   "hc/set-size-key!"
   "hc/set-strokedash-key!"
   "hc/set-title-key!"
   "hc/shape-key"
   "hc/size-key"
   "hc/strokedash-key"
   "hc/subkeyfns"
   "hc/title-key" 
   "hc/update-defaults" 
   "hc/update-subkeyfns" 
   "hc/xform"])

(def hanami-template-names
  ["ht/RMV"
   "ht/area-chart"
   "ht/area-layer"
   "ht/bar-chart"
   "ht/bar-layer"
   "ht/contour-plot"
   "ht/corr-heatmap"
   "ht/data-options"
   "ht/default-col"
   "ht/default-color"
   "ht/default-config"
   "ht/default-mark-props"
   "ht/default-row"
   "ht/default-title"
   "ht/default-tooltip"
   "ht/empty-chart"
   "ht/encoding-base"
   "ht/gen-encode-layer"
   "ht/grouped-bar-chart"
   "ht/hconcat-chart"
   "ht/heatmap-chart"
   "ht/interval-brush-mdwn"
   "ht/interval-brush-smdwn"
   "ht/interval-scales"
   "ht/layer-chart"
   "ht/line-chart"
   "ht/line-layer"
   "ht/mark-base"
   "ht/overview-detail"
   "ht/point-chart"
   "ht/point-layer"
   "ht/rect-layer"
   "ht/text-encoding"
   "ht/text-layer"
   "ht/tree-layout"
   "ht/ttdef"
   "ht/vconcat-chart"
   "ht/view-base" 
   "ht/xrule-layer" 
   "ht/xy-encoding" 
   "ht/yrule-layer"])

(def js-hint-names
  (->> cljs-core-names
       (concat cljs-str-names
               saite-core-names
               hanami-core-names
               hanami-common-names
               hanami-template-names)
       sort vec))





(comment
  ;; It makes no sense, but these will not properly run on initial loading.
  ;; So, must run before build, get the values and insert above
  (def saite-core-names
    (->> 'aerial.saite.core ns-publics (mapv #(-> % first name)) sort
         (mapv #(str "sc/" %))))

  (def hanami-core-names
    (->> 'aerial.hanami.core ns-publics (mapv #(-> % first name)) sort
         (mapv #(str "hmi/" %))))

  (def hanami-common-names
    (->> 'aerial.hanami.common ns-publics (mapv #(-> % first name)) sort
         (mapv #(str "hc/" %))))

  (def hanami-template-names
    (->> 'aerial.hanami.templates ns-publics (mapv #(-> % first name)) sort
         (mapv #(str "ht/" %))))
  )