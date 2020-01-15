(ns paredit-cm.core
  "paredit operations (exported)"
  (:require [clojure.string :as str]
            [cljsjs.codemirror]
            [cljsjs.codemirror.mode.clojure]
            [cljsjs.codemirror.keymap.emacs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; MIT License
;;
;; Copyright (c) 2017 Andrew Cheng
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included
;; in all copies or substantial portions of the Software.
;;
;; Jon Anthony (2019,2020):
;; Many changes and fixes for working with newer codemirror releases
;;
;; ** PAREDI PROJECT CONVENTIONS **
;;
;; consider this notation: aXbc
;;
;; in the unit tests as well as here, aXbc contains a single capital X which
;; represents the position of the cursor. aXbc means the code mirror instance's
;; value is 'abc' and a block-style cursor is on 'b' (a bar-style cursor would
;; be between 'a' and 'b'). aXbc is what you would see if you typed a capital X
;; in this example code mirror.
;;
;; 'cur' is for the current position's cursor (on 'b' in the example).
;; 'left-cur' is for position 'a'. 'right-cur' is for position 'c'.
;;
;; if there is a current cursor cur and a new cursor, then the new cursor will
;; be named cur' (the single quote is part of the name, so read it aloud as
;; cursor-prime)
;;
;; when there are two cursors (as in the beginning and ending of a selection) we
;; use c1 and c2. it feels strange to call them 'start' and 'end' when those are
;; the names codemirror uses to refer to the ends of a token.
;;
;; the following all refer to the values for the token at 'cur': 'start' 'line'
;; 'ch' 'i' 'string' 'type'
;;
;; use the same prefixes 'left-' and 'right-' when referring to the same kinds
;; of values belonging to 'left-cur' and 'right-cur'
;;
;; ints *other than i, the code mirror index* are named with a single character
;; like 'x'. neighboring values are represented alphabetically, so (inc x) would
;; be named 'y' and (dec x) would be named 'w'.
;;
;; s1 is a string. similarly s1, s2, and s
;;
;; for numerical values like 'offset', lower is for left and higher is for
;; right, just as for code mirror's index i.
;;
;; sp is a 'skipping predicate'. these are used with a trampoline wrapper like
;; 'skip' to move along the text in code mirror until our predicate is
;; satisfied. in many cases, the predicate will push and pop openers/closers off
;; a stack and when the stack is empty and we satisfy some additional condition,
;; then we stop and return the cursor.
;;
;; functions with names ending in -sp are skipping predicates.
;;
;; currently we're assuming perfect matching of openers/closers so we don't
;; actually keep track of the stack -- we just inc and dec an int until it gets
;; to 0 and our other conditions are satisfied
;;
;; any trampoline use should be limited by the cm character count, to guard
;; against infinite loops. we'll start at the limit and count down, stopping
;; when it goes negative.
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(enable-console-print!)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; general helper methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def openers #{ "(" "[" "{" })
(def closers #{ ")" "]" "}" })

(def pair {"(" ")", "[" "]", "{" "}", "\"" "\"",
           ")" "(", "]" "[", "}" "{"})

(defn pair?
  "true if the two strings are a matching open/close pair "
  [s1 s2]
  (= (pair s1) s2))

(defn opener? [s] (contains? openers s))
(defn closer? [s] (contains? closers s))

(defn is-bracket-type?
  [t]
  (and t (str/starts-with? t "bracket")))

(defn char-count
  "returns the number of characters in the code mirror instance"
  [cm]
  (-> cm .getValue count))

(defn cursor
  "get cur, the position of the cursor"
  ([cm] (.getCursor cm)) ;; get current cursor
  ([cm i] (.posFromIndex cm i))) ;; get cursor for index i

(defn index
  "get the index i for the cursor's position"
  ([cm] (index cm (cursor cm)))
  ([cm cur] (when cur (.indexFromPos cm cur))))

(defn bof?
  "true if at beginning of file"
  [cm cur]
  (zero? (index cm cur)))

(defn eof?
  "true if at end of file"
  [cm cur]
  (= (index cm cur) (char-count cm)))

(defn token
  "get token at cursor"
  [cm cur]
  (.getTokenAt cm cur true))

(defn get-type
  "get the type at the current cursor."
  ([cm]
   (get-type cm (cursor cm)))
  ([cm cur]
   (.-type (token cm cur))))

(defn get-string
  "gets the string of the current token"
  ([cm] (get-string cm (cursor cm)))
  ([cm cur] (when cur (.-string (token cm cur)))))

(defn line-length
  "gets the length of the current line"
  ([cm] (line-length cm (cursor cm)))
  ([cm cur] (when cur (count (.getLine cm (.-line cur))))))

(defn last-token
  "returns the last token of a line"
  [cm cur]
  (->> cur .-line (.getLineTokens cm) last))

(defn last-cur
  "returns the last cursor of a line"
  ([cm] (last-cur cm (cursor cm)))
  ([cm cur] (let [end (.-end (last-token cm cur))
                  diff (- end (.-ch cur))]
              (cursor cm (+ diff (index cm cur))))))

(defn get-info
  "make info from CodeMirror more conveniently accessed by our code.
  we'll use destructuring and just name what we rant. hypothesizing
  that performance hit won't be that bad."
  ([cm] (get-info cm (cursor cm)))
  ([cm cur]
   (when cur (let [tok (token cm cur)
                   eof (eof? cm cur)
                   bof (bof? cm cur)
                   i   (index cm cur)
                   left-cur  (when-not bof (cursor cm (dec i)))
                   right-cur (when-not eof (cursor cm (inc i)))]
               {:cur    cur
                :line   (.-line cur)
                :ch     (.-ch   cur)
                :i      i
                :tok    tok
                :string (.-string tok)
                :start  (.-start  tok)
                :end    (.-end    tok)
                :type   (.-type   tok)
                :top    (-> tok .-state .-indentStack nil?) ;; true for toplevel
                :eof    eof
                :bof    bof
                :left-char  (when-not bof (.getRange cm left-cur cur))
                :right-char (when-not eof (.getRange cm cur right-cur))
                :left-cur   left-cur
                :right-cur  right-cur
                :mode   (.-mode (.-state tok))}))))

(defn comment-or-string?
  "true if the type is comment or string. a lot of editing behavior (like
  movement and deletion) is similar when you are in a string or in a comment, so
  often this is the predicate for that behavior."
  [type]
  (or (= type "comment")
      (= type "string")))

(defn indent-line
  "indent the current line"
  [cm]
  (->> cm cursor .-line (.indentLine cm)))

(defn escaped-char-name? [stg]
  (let [escnames #{"\\newline", "\\space", "\\tab",
                   "\\formfeed", "\\backspace", "\\return"}]
    (when (escnames stg) (dec (count stg)))))

(defn in-escaped-char?
  "returns true if backslash is to the left and cursor is on an escaped char"
  ([cm cur]
   (in-escaped-char? cm cur 0))
  ([cm cur offset]
   (let [{:keys [ch start end type]} (get-info cm cur)]
     #_(js/console.log start ch end type)
     (and (= type "string-2") (and (< start ch) (< ch end))))))

(defn escaped-char-to-left?
  "returns true if an escaped char and its backslash are to the left"
  [cm cur]
  (let [{:keys [ch end type string]} (get-info cm cur)]
    (and (= type "string-2") (= ch end))))


(defn escaped-char-to-right?
  "returns true if an escaped char and its backslash is to the right"
  [cm cur]
  (let [cur+ (cursor cm 0)
        {:keys [type]} (get-info cm cur)]
  (and (not= type "string-2"))
    (set! cur+.line cur.line)
    (set! cur+.ch (inc cur.ch))
    (in-escaped-char? cm cur)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-open-round (
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn insert
  "insert text at current cursor. move cursor to the end of inserted text minus
  optional offset. the offset is for moving the cursor immediately after the
  insert and before returning. example: inserting a pair of brackets and placing
  the cursor inside the pair. this returns the new cursor."
  ([cm text] (insert cm text 0))
  ([cm text offset] (insert cm text offset (cursor cm)))
  ([cm text offset cur]
   (let [{:keys [line ch]} (get-info cm cur)]
     (.replaceRange cm text cur)
     (.setCursor cm line (+ (+ ch (count text)) offset))
     (cursor cm))))

(defn ^:export open-round
  "paredit-open-round exposed for keymap. unlike traditional emacs paredit, this
  supports brackets [] {} () but not double-quote"
  ([cm] (open-round cm "("))
  ([cm c]
   (let [{:keys [type left-char right-char]} (get-info cm)]
     (cond
       ;; escaping the next character:
       (= "\\" left-char) (insert cm c)

       ;; typing in a comment or string as-is:
       (comment-or-string? type) (insert cm c)

       ;; insert a pair, pad with a space to the left and/or right if necessary,
       ;; and move the cursor into the pair before returning:
       :else
       (let [need-left-padding (and (not= " " left-char)
                                    (not (opener? left-char)))
             need-right-padding (and (not= " " right-char)
                                     (not (closer? right-char)))]
         (insert cm
                 (str (when need-left-padding " ")
                      c (pair c)
                      (when need-right-padding " "))
                 (if need-right-padding -2 -1)))))))

(defn ^:export open-brace
  "open curly brace with matching close brace"
  ([cm] (open-round cm "{")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-close-round )
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parent-closer-sp ;; -sp see 'skipping predicate' below
  "finds the *parent* closing bracket. behavior when used with skip: pushes
  opening brackets that appear along the way on a stack. closing brackets pop
  them off. stops when encountering a closing bracket while the stack is empty.
  assuming the cm has matched brackets for now. moves to the right."
  [cm cur state]
  (let [{:keys [string type top eof]} (get-info cm cur)]
    (cond
      ;; 'push' opener on our 'stack':
      (and (is-bracket-type? type) (opener? string)), (inc state)

      ;; stop if we see a closer while our 'stack' is empty:
      (and (is-bracket-type? type) (closer? string) (zero? state)), :yes

      ;; closer means we 'pop' off the 'stack', unless eof
      (and (is-bracket-type? type) (closer? string) (not= 0 state) eof), :eof

      ;; closer means we 'pop' off the 'stack':
      (and (is-bracket-type? type) (closer? string) (not= 0 state)), (dec state)

      ;; we can* rely on code mirror to tell us if we're at the top
      ;; level: (* NOT in [cljsjs/codemirror "5.21.0-2"] ... but maybe
      ;; in a later version ... until we can figure out how to refer
      ;; to the latest codemirror in our tests, the tests will have to
      ;; live here in order to get the codemirror that is included in
      ;; the script tag on the demo index.html page)
      ;; TODO: investigate whether we can use this, given CodeMirror version:
      ;; top, :stop

      ;; stack stays unchanged. move to the next thing:
      :default, state)))

(defn token-start
  "returns the cursor for the start of the current token"
  [cm cur]
  (let [{:keys [i line start ch type]} (get-info cm cur)]
    (cursor cm (- i (- ch start)))))

(defn token-end
  "returns the cursor for the end of the current token"
  ([cm cur] (token-end cm cur 0))
  ([cm cur offset]
   (let [{:keys [i line end ch type]} (get-info cm cur)]
     (cursor cm (+ i offset (- end ch))))))

(defn token-end-index
  "take an index. get its token. return index of that token's end."
  [cm i]
  (->> i
       (cursor cm)
       (token-end cm)
       (index cm)))

(defn guard [] (println "past"))

(defn skip-trampoline-helper
  "returns the cursor that satsifies skipping predicate 'sp' or nil if eof
  reached. does this by making sp something we can trampoline. sp takes these
  args: cm, cursor, state. counts down 'n' to 0 in order to guard against
  infinite loops."
  [cm cur sp state n]
  (if (>= n 0)
    (let [{:keys [left-cur right-cur i]} (get-info cm cur)
          result (sp cm cur state)]
      #_(js/console.log result)
      (case result
        :eof               nil
        :stop              nil
        :yes               cur
        :left              left-cur
        :right             right-cur
        :end-of-this-token (token-end cm cur)
        :start-of-this-tok (token-start cm cur)
        (let [next-cur (token-end cm cur 1)] #_(js/console.log next-cur)
          (fn [] ;; for trampoline
            (skip-trampoline-helper cm next-cur sp result (dec n))))))
    (guard)))

(defn skip-trampoline-helper-left
  "like skip-trampoline-helper but in the opposite direction."
  [cm cur sp state n]
  (if (>= n 0)
    (let [{:keys [left-cur right-cur i start ch]} (get-info cm cur)
          result (sp cm cur state)]
      #_(js/console.log result)
      (case result
        :bof               nil
        :stop              nil
        :yes               left-cur
        :right             right-cur
        :end-of-this-token (token-end cm cur)
        :start-of-this-tok (token-start cm cur)
        (let [next-cur (if (= ch start)
                         (cursor cm (dec i))
                         (cursor cm (- i (- ch start))))]
          (fn [] ;; for trampoline
            (skip-trampoline-helper-left cm next-cur sp result (dec n))))))
    (guard)))

(defn skip
  "returns the cursor that satisfies sp or nil if either eof reached
  or we found out sp could not be satisfied. see skip-to for more
  info."
  ([cm sp] (skip cm sp (cursor cm)))
  ([cm sp cur]
   (when-let [right-cur (:right-cur (get-info cm cur))]
     (trampoline skip-trampoline-helper cm right-cur sp 0 (char-count cm)))))

(defn skip-left
  "returns the cursor that satisfies sp or nil if either bof reached
  or we found out sp could not be satisfied. see skip-to for more
  info."
  [cm sp]
  (when-let [cur (cursor cm)]
    (trampoline skip-trampoline-helper-left cm cur sp 0 (char-count cm))))

(defn delete-whitespace
  "if cur is in whitespace, deletes it optionally without ruining indentation."
  ([cm] (delete-whitespace cm (cursor cm) true))
  ([cm cur] (delete-whitespace cm cur true))
  ([cm cur indent-after]
   (let [{:keys [start end line ch i type]} (get-info cm cur)
         c1 (cursor cm (+ i (- start ch)))
         c2 (cursor cm (+ i (- end   ch)))]
     (when (nil? type)
       (.replaceRange cm "" c1 c2)
       (if indent-after (.indentLine cm line))))))
;; todo
(defn just-one-space
  ([cm] (just-one-space cm (cursor cm) true))
  ([cm cur] (just-one-space cm cur true))
  ([cm cur indent-after]
   (let [{:keys [start end line ch i type]} (get-info cm cur)
         c1 (cursor cm (+ i (- start ch)))
         c2 (cursor cm (+ i (- end   ch)))]
     (when (nil? type)
       (.replaceRange cm " " c1 c2)
       (if indent-after (.indentLine cm line))))))

(defn skip-to
  "moves to the cursor that satisfies sp or doesn't move if eof reached.
  starts at current cursor for cm. sp stands for 'skipping predicate'
  which returns:
  - :yes if sp is satisfied.
  - :stop if we know we will not be satisfied with any future result.
  - :left if the cursor to the left is what we want.
  - new non-nil state if not satisfied. this state is used with the
  next iteration after we skip to the end of the current token. an sp
  takes cm, cursor, state."
  [cm sp]
  (when-let [cur' (skip cm sp)]
    (.setCursor cm cur')
    cur'))

(defn move-past-parent-closer
  "moves cursor to just outside the closing bracket, or if there is
  none then doesn't move at all."
  ;; emacs has this extending the current selection if there is one.
  [cm]
  (when-let [cur (skip-to cm parent-closer-sp)]
    (delete-whitespace cm (:left-cur (get-info cm)))
    cur))

(defn ^:export close-round
  "paredit-close-round exposed for keymap. skips to end of current
  list even if it ends with ] or }. but if you're in a string or
  comment then this just inserts the bracket. requires CodeMirror
  mode's parser uses state with indentStack because that's how we
  can tell we've reached the end of a top level form and avoid
  entering the next top level form. 's' is the character as a string."
  ([cm] (close-round cm ")"))
  ([cm s]
   (let [{:keys [type left-char]} (get-info cm)]
     (cond
       (= "\\" left-char)        (insert cm s)
       (comment-or-string? type) (insert cm s)
       :else                     (move-past-parent-closer cm)))))

(defn ^:export close-brace
  "close curly brace like close-rond"
  ([cm] (close-round cm "}")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-close-round-and-newline paredit-open-square paredit-close-square
;; paredit-doublequote
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export close-round-and-newline
  ([cm] (close-round-and-newline cm ")"))
  ([cm s]
   (if (comment-or-string? (get-type cm))
     (insert cm s)
     (when (close-round cm s)
       (.execCommand cm "newlineAndIndent")))))
;; question: is there a better way than .execCommand?

(defn ^:export  open-square [cm]  (open-round cm "["))
(defn ^:export close-square [cm] (close-round cm "]"))

(defn ^:export doublequote [cm]
  (let [{:keys [type left-char right-char ch cur]} (get-info cm)]
    (cond
      ;; about to escape this char so insert as-is:
      (= "\\" left-char) (insert cm "\"")

      ;; we're in a string so escape this doublequote:
      (= type "string")  (insert cm "\\\"")

      ;; we're in code. pad with a space to the left and/or right if necessary
      ;; to separate it from neighboring code. after inserting, move the cursor
      ;; to between the quotes:
      :else (insert cm
                    (str (when (not= " " left-char) " ") ;; left padding
                         "\"\""
                         (when (and (not= " " right-char)
                                    (not= "\n" right-char))
                           " ")) ;; right padding
                    (if (or (= " " right-char)
                            (= "\n" right-char)) -1 -2)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-meta-doublequote M-"
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn word? [type]
  (or (= type "atom")
      (= type "builtin")
      (= type "number")
      (= type "variable")
      (= type "keyword")
      (= type "meta")))

(defn at-a-word?
  "returns true if at a word of code"
  [cm cur]
  (word? (get-type cm cur)))

(defn in-a-word?
  "true if in a word AND not at the end of that word. false if in whitespace or
  a string or a comment or at a bracket."
  [cm]
  (let [cur (cursor cm), i (index cm cur)]
    (and (at-a-word? cm cur)
         (not= i (token-end-index cm i)))))

(defn start-of-a-string?
  "returns true if at the start of a string."
  [cm cur]
  (let [{:keys [string type start ch left-char]} (get-info cm cur)]
    #_(js/console.log right-char type string ch start)
    (and (= left-char "\"")
         (= type "string")
         (= 1 (- ch start)))))

(defn start-of-a-string2? [cm cur]
  (let [i (index cm cur)
        p2 (cursor cm (inc i))]
    #_(js/console.log cur p2)
    (start-of-a-string? cm p2)))

(defn end-of-a-string?
  "returns true if just to the right of a closing doublequote of a string."
  [cm cur]
  (let [{:keys [type ch end string left-char]} (get-info cm cur)]
    #_(js/console.log left-char type string ch end)
    (and (= type "string")
         (= ch end)
         (= left-char "\""))))

(defn end-of-next-sibling-sp ;; -sp see 'skipping predicate'
  "returns the cursor at the end of the sibling to the right or nil if
  no sibling or eof. does not exit the containing form. does this by
  skipping past any comments or whitespace, and branches depending on
  whether an opening bracket or doublequote is encountered (sp
  satisfied when encountering a closing bracket that empties the
  stack) vs the beginning of a word (return token at the end of the
  word). assuming the cm has matched brackets for now."
  [cm cur stack]
  (let [{:keys [string type eof ch end]} (get-info cm cur)
        stack-empty (zero? stack)
        one-left    (= 1 stack)
        ;; for multi-line strings
        string-extends (or (not= "\"" (last string))
                           (= "\\" (last (drop-last string))))]
    (js/console.log stack stack-empty string type ch end cur string-extends
                    #_(escaped-char-to-right? cm cur)
                    (start-of-a-string? cm cur)
                    (end-of-a-string? cm cur))
    (cond ;; we return a keyword when we know where to stop, stack otherwise.

      ;; skip whitespace
      (or (nil? type) (and (= type "error") (= string ","))), stack

      (and (escaped-char-to-left? cm cur) stack-empty), :yes
      (and (word? type) stack-empty (= ch end)), :yes
      (and (is-bracket-type? type) (closer? string) one-left), :yes
      (and (end-of-a-string? cm cur) one-left), :yes

      eof, :eof

      ;; skip comments
      (= type "comment"), stack

      ;; strings ...............................................................

      ;; our starting point is at beginning of a string and it doesn't extend
      (and (start-of-a-string? cm cur)
           (and (not string-extends) stack-empty)), :end-of-this-token

      ;; We are in a nested form, at start of string, but it doesn't extend
      (and (start-of-a-string? cm cur)
           (not stack-empty)
           (not string-extends)), stack

      ;; entering a multi-line string, push " onto stack
      (and (start-of-a-string? cm cur)
           string-extends), (inc stack)

      ;; at end of string and stack already empty, we must have started in the
      ;; middle of the string
      (and (end-of-a-string? cm cur) stack-empty), :stop

      ;; at end of string and stack about to be empty, we've found the end of
      ;; the string -- handled before checking for eof above

      ;; in string, the end of this string is our goal ...
      ;; ... but the end of this string is on a different line:
      (and (= type "string")
           #_(not stack-empty) #_one-left
           string-extends), stack

      (and (= type "string")
           stack-empty
           (not string-extends)), :end-of-this-token

      ;; in string, the end of this string is our goal ...
      ;; ... the end is on this line:
      (and (= type "string") one-left), :end-of-this-token

      ;; in string, need to get out of this form, pop stack
      (and (= type "string")
           (not stack-empty)), (dec stack)


      ;; escaped chars .........................................................

      ;; inside an escaped char and the end of it is what we want
      (and (in-escaped-char? cm cur) stack-empty), :end-of-this-token

      ;; To the right of escaped char, keep going
      (and (escaped-char-to-right? cm cur) stack-empty), :start-of-this-tok


      ;; in an escaped char inside the next sibling
      (in-escaped-char? cm cur), stack

      ;; at end of an escaped char which was the next sibling -- handled before
      ;;checking for eof above

      ;; at end of an escaped char inside the next sibling
      (escaped-char-to-left? cm cur), stack

      ;; words .................................................................

      ;; reached the end of a word which was the next sibling -- handled before
      ;;checking for eof above

      ;; in a word that is the next sibling, the end of it is what we want
      (and (word? type) stack-empty), :end-of-this-token

      ;; in a word that is inside the next sibling
      (word? type), stack

      ;; brackets ..............................................................

      ;; push opener on stack
      (and (is-bracket-type? type) (opener? string)), (inc stack)

      ;; we've reached the end of a form -- handled before checking for eof
      ;;above

      ;; there was no sibling
      (and (is-bracket-type? type) (closer? string) stack-empty), :stop

      ;; passing through the guts of a sibling form (.. (guts)|..)
      (and (is-bracket-type? type) (closer? string)), (dec stack)

      :default, :stop)))

(defn end-of-next-sibling
  "get the cursor for the end of the sibling to the right."
  ([cm]
   (skip cm end-of-next-sibling-sp))
  ([cm cur]
   (when cur
     (.setCursor cm cur)
     (skip cm end-of-next-sibling-sp))))

(defn start-of-prev-sibling-sp ;; -sp see 'skipping predicate'
  "returns the cursor at the start of the sibling to the left or nil
  if no sibling or eof. does not exit the containing form. does this
  by skipping past any comments or whitespace, and branches depending
  on whether a bracket or doublequote is encountered (sp satisfied
  when encountering an opening bracket that empties the stack) vs the
  beginning of a word (return token at the start of the
  word). assuming the cm has matched brackets for now."
  [cm cur stack]
  (let [{:keys [string type bof ch start]} (get-info cm cur)
        stack-empty (zero? stack)
        one-left    (= 1 stack)
        string-extends (not= "\"" (first string))];; for multiline strings
    (js/console.log stack stack-empty string type ch start cur string-extends
                    ;;(escaped-char-to-left? cm cur)
                    ;;(escaped-char-to-right? cm cur)
                    (start-of-a-string? cm cur)
                    (end-of-a-string? cm cur))
    (cond ;; we return a keyword when we know where to stop, stack otherwise.

      ;; check these before checking for bof:

      ;; in a multi-line string, keep searching for the first line of it:
      (and (start-of-a-string? cm cur) one-left string-extends), stack

      ;; at the first line of a string and we want its opening doublequote:
      (and (start-of-a-string? cm cur) one-left), :yes

      ;; at the start of a word:
      (and (word? type) stack-empty (= ch start)), :yes

      ;; at the opener we were looking for:
      (and (is-bracket-type? type) (opener? string) one-left), :yes

      bof, :bof; reached beginning of file

      (and (start-of-a-string2? cm cur)
           (not stack-empty)), stack #_(dec stack)

      ;; at the start of an escaped char:
      (and (escaped-char-to-right? cm cur) stack-empty), stack ;:start-of-this-tok

      ;; skip whitespace
      (or (nil? type) (and (= type "error") (= string ","))), stack


      ;; skip comments
      (= type "comment"), stack

      ;; strings ...............................................................

      ;; our starting point is at end of a string and it doesn't extend
      (and (end-of-a-string? cm cur)
           (and (not string-extends) stack-empty)), :start-of-this-tok

      ;; We are in a nested form, at end of string, but it doesn't extend
      (and (end-of-a-string? cm cur)
           (not stack-empty)
           (not string-extends)) stack

      ;; entering a multi-line string from the right; push " onto stack
      (and (end-of-a-string? cm cur)
           string-extends), (inc stack)

      ;; at start of string and stack already empty, we must have started in
      ;; the middle of the string.
      (and (start-of-a-string? cm cur)
           stack-empty), :stop

      ;; at start of string and stack about to be empty, we've found the end of
      ;; the string -- handled before check for bof above

      ;; in string, the start of it is our goal ...
      ;; ... but the start of this string is on a higher line:
      (and (= type "string")
           #_(not stack-empty)
           string-extends), stack

      ;; it's on this line:
      (and (= type "string")
           stack-empty
           (not string-extends)), :start-of-this-tok

      ;; in string, the start of this string is our goal ...
      ;;; ... and the start is on this line:
      (and (= type "string") one-left) :start-of-this-tok

      ;; in string, need to get out of this form, pop stack
      (and (= type "string")
           (not stack-empty)), (dec stack)


      ;; escaped chars .........................................................

      ;; inside an escaped char and the start of it is what we want
      (and (in-escaped-char? cm cur) stack-empty), :start-of-this-tok

      ;; To the left of escaped char, keep going
      (and (escaped-char-to-left? cm cur) stack-empty), :start-of-this-tok

      ;; in an escaped char inside the prev sibling
      (or (in-escaped-char? cm cur)
          (escaped-char-to-left? cm cur)), stack

      ;; at start of an escaped char which was the prev sibling -- handled
      ;; before check for bof above

      ;; at start of an escaped char inside the prev sibling
      (escaped-char-to-right? cm cur), stack

      ;; words .................................................................

      ;; reached the start of a word which was the prev sibling -- handled
      ;; before check for bof above

      ;; in a word that is the prev sibling, the start of it is what we want
      (and (word? type) stack-empty), :start-of-this-tok

      ;; in a word that is inside the prev sibling
      (word? type), stack

      ;; brackets ..............................................................

      ;; push closer on stack
      (and (is-bracket-type? type) (closer? string)), (inc stack)

      ;; we've reached the start of a form -- handled before check for
      ;; bof above

      ;; there was no prev sibling, avoid exiting the form
      (and (is-bracket-type? type) (opener? string) stack-empty), :stop

      ;; passing through the guts of a sibling form (.. X(guts)..)
      (and (is-bracket-type? type) (opener? string)), (dec stack)

      :default :stop)))

(defn start-of-prev-sibling
  "return the cursor at the start of the sibling to the left."
  ([cm]
   (skip-left cm start-of-prev-sibling-sp))
  ([cm cur]
   (when cur
     (.setCursor cm cur)
     (skip-left cm start-of-prev-sibling-sp))))

(defn escape-string
  "escapes a string, replacing backslashes and doublequotes. wraps
  result in a new pair of doublequotes."
  [s]
  (str "\""
       (-> s
           (str/replace #"[\\]" "\\\\")
           (str/replace #"[\"]" "\\\""))
       "\""))

(defn stringify-selection
  "turns selection into a string, escaping backslashes and doublequotes"
  [cm]
  (->> cm .getSelection escape-string (.replaceSelection cm)))

(defn stringify
  "turns the region from cur-1 to cur-2 into a string, escaping
  backslashes and doublequotes"
  [cm cur-1 cur-2]
  (.setSelection cm cur-1 cur-2)
  (stringify-selection cm)
  (.setCursor cm (cursor cm (inc (index cm cur-1)))))

(defn exit-string
  "moves cursor right, out of the current string"
  [cm]
  (let [{:keys [type i ch end]} (get-info cm)]
    (when (= type "string")
      (.setCursor cm (cursor cm (+ i (- end ch)))))))

(defn in-string?
  "returns true if token is in the middle of a string."
  ([cm] (in-string? cm (cursor cm)))
  ([cm cur]
   (let [type (get-type cm cur)]
     (or (= type "string")
         (= type "string-2")))))

(defn ^:export meta-doublequote
  "paredit meta-doublequote exposed for keymap.
  if in a string, moves cursor out of the string to the right.
  if in a comment, insert a doublequote.
  if in an escaped char, do nothing.
  otherwise starts a string that that continues to the end of the next
  form, escaping backslashes and doublequotes."
  [cm]
  (let [{:keys [type eof cur]} (get-info cm)]
    (cond
      eof                       :do-nothing
      (in-escaped-char? cm cur) :do-nothing
      (in-string? cm cur)       (exit-string cm)
      (= type "comment")        (insert cm "\"")
      (in-a-word? cm)           (stringify cm cur (token-end cm cur))
      :else                     (stringify cm cur (end-of-next-sibling cm)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-comment-dwim
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn left
  "given a pair of cursors c1 and c2, returns the left-most one"
  [cm c1 c2]
  (let [i1 (index cm c1)
        i2 (index cm c2)]
    (if (< i1 i2) c1 c2)))

(defn right
  "given a pair of cursors c1 and c2, returns the right-most one"
  [cm c1 c2]
  (let [i1 (index cm c1)
        i2 (index cm c2)]
    (if (< i1 i2) c2 c1)))

(defn selection-info
  "like get-info but for the first selection. gets the cursor to the left of the
  selection, the start, the end, the text selected, the starting and ending line
  numbers. nil if nothing selected."
  [cm]
  (when (.somethingSelected cm)
    (let [first-sel (-> cm .listSelections first)
          text      (-> cm  .getSelections first)
          anchor (.-anchor first-sel)
          head   (.-head   first-sel)
          left-of-start (left cm anchor head)
          start-cur (cursor cm (inc (index cm left-of-start)))
          end-cur (right cm anchor head)]
      [left-of-start start-cur end-cur text
       (.-line start-cur) (.-line end-cur)])))

(defn get-types
  "get the types from cursors c1 to c2. assumes 1 is to the left of 2 and not
  vice versa."
  [cm c1 c2]
  (loop [types [], cur c1]
    (let [{:keys [type right-cur]} (get-info cm cur)
          types' (conj types type)]
      (if (= cur c2)
        types'
        (recur types' right-cur)))))

(defn selection-completely-satisfies-pred?
  "true if every position's type satisfies pred, for the entire (first)
  selection"
  [cm pred]
  (when-let [[_ c1 c2] (selection-info cm)]
    (every? pred (get-types cm c1 c2))))

(defn selection-completely-whitespace? [cm]
  (selection-completely-satisfies-pred? cm nil?))

(defn not-code? [type] (or (nil? type) (= type "comment")))

(defn selection-completely-non-code? [cm]
  (selection-completely-satisfies-pred? cm not-code?))

(defn to-comment
  "starts each line in 's' with ;; and appends 'post-script'"
  [s postscript]
  (let [cmnt (->> s
                  str/split-lines
                  (map #(str/replace % #"^" ";; "))
                  (str/join "\n"))]
    (str cmnt "\n" postscript)))

(defn uncomment
  "removes leading whitespace and semicolons from lines in 's'"
  [s]
  (->> s
       str/split-lines
       (map #(str/replace % #"^\s*;+" ""))
       (str/join "\n")))

(defn indent-lines
  "indents lines from a to z (line numbers). assumes a is before z."
  [cm a z]
  (doseq [line (range a (inc z))]
    (.indentLine cm line)))

(defn uncomment-selection
  "removes whitespace and leading semicolons from selection, replaces
  selection with the result, indents lines affected."
  [cm]
  (when-let [[_ c1 c2 text] (selection-info cm)]
    (.replaceSelection cm (uncomment text))
    (indent-lines cm (.-line c1) (.-line c2))))

(defn append
  "returns the result of appending the applicable part of 'tok' to
  's'. this is for collecting all the text on a line after 'ch'"
  [ch s tok]
  (if (< ch (.-end tok))
    (str s (subs (.-string tok) (- (max ch (.-start tok)) (.-start tok))))
    s))

(defn get-text-to-end-of-line
  [cm cur]
  (let [toks (.getLineTokens cm (.-line cur))
        ch (.-ch cur)]
    (reduce (partial append ch) "" toks)))

(defn comment-selection [cm]
  (let [[_ c1 c2 text l1 l2] (selection-info cm)
        text-after-selection   (get-text-to-end-of-line cm c2)
        code-follows-selection (not= text-after-selection "")
        end-of-line            (last-cur cm)
        line-to                (if code-follows-selection (inc l2) l2)]
    (when code-follows-selection
      (.setSelection cm left end-of-line))
    (.replaceSelection cm (to-comment text text-after-selection))
    (indent-lines cm l1 line-to)))

(defn line-ends-with-comment?
  "true if the line ends with a comment"
  [cm]
  (= "comment" (.-type (last-token cm (cursor cm)))))

(defn indent-current-line [cm] (->> cm cursor .-line (.indentLine cm)))

(defn go-to-comment
  "moves cursor to ;;X"
  [cm]
  (let [cur (cursor cm)
        ch  (.-ch cur)
        i   (index cm cur)
        c-tok (last-token cm cur)
        start (.-start c-tok)
        offset (count (take-while #(= ";" %) (.-string c-tok)))]
    (.setCursor cm (cursor cm (+ i (- start ch) offset)))))

(defn insert-spaces-to-col-40
  "presses spacebar until we are at col 40"
  [cm]
  (let [ch (-> cm cursor .-ch)]
    (when (< ch 40)
      (insert cm (str/join (repeat (- 40 ch) " "))))))

(defn go-to-comment-and-indent
  "moves cursor to the comment on the line and makes sure the comment
  starts on column 40 or greater. assumes last token is a comment"
  [cm]
  (indent-current-line cm)
  (let [cur (cursor cm)
        ch  (.-ch cur)
        i   (index cm cur)
        comment-start (.-start (last-token cm cur))]
    (.setCursor cm (cursor cm (+ i (- comment-start ch))))
    (insert-spaces-to-col-40 cm)
    (go-to-comment cm)))

(defn betw-code-and-line-end?
  "true if code is to the left and whitespace* is to the right.
  assumes you already know line does not end with a comment."
  [cm]
  (let [cur   (cursor cm)
        toks  (.getLineTokens cm (.-line cur))
        ch    (.-ch cur)
        tests (map #(or (<= (.-end %) ch)
                        (nil? (.-type %))) toks)]
    (and (seq toks) ; the line is not empty
         (every? true? tests) ; there's only whitespace to the right
         (some #(not (nil? (.-type %))) toks)))) ; there's code on the left

(defn move-to-end-of-line
  "moves cursor to end of last non-whitespace token on a line.
  returns a vector of new index, new ch, and new cursor."
  ([cm] (move-to-end-of-line cm (cursor cm)))
  ([cm cur]
   (let [end (->> cur .-line (.getLineTokens cm) (remove #(nil? (.-type %)))
                  last .-end)
         ch  (.-ch cur)
         i   (index cm cur)
         i'  (+ i (- end ch))
         cur' (cursor cm i')]
     (.setCursor cm cur')
     [i' (.-ch cur') cur'])))

(defn select-rest-of-line
  "selects from current position to the end of the line"
  [cm]
  (.setSelection cm (cursor cm) (last-cur cm)))

(defn delete-to-end-of-line
  "deletes from current position to the end of the line"
  [cm]
  (.replaceRange cm "" (cursor cm) (last-cur cm)))

(defn create-comment-at-end
  "starts a ; comment at column 40 or greater and moves to it."
  [cm]
  (indent-current-line cm)
  (move-to-end-of-line cm)
  (insert cm " ")
  (insert-spaces-to-col-40 cm)
  (insert cm "; ")
  (delete-to-end-of-line cm))

(defn line-is-whitespace?
  "returns true if line is all whitespace"
  [cm]
  (->> cm cursor .-line (.getLineTokens cm) (every? #(nil? (.-type %)))))

(defn create-line-comment
  "creates and indents a ;; comment"
  [cm]
  (insert cm ";; ")
  (delete-to-end-of-line cm)
  (indent-current-line cm))

(defn new-line-and-comment
  "creates and indents a ;; comment on a new line"
  [cm]
  (indent-current-line cm)
  (insert cm "\n\n")
  (.execCommand cm "goLineDown")
  (.execCommand cm "goLineDown")
  (indent-current-line cm)
  (.execCommand cm "goLineUp")
  (create-line-comment cm))

(defn insert-line-comment-here
  "creates and indents a ;; comment on this line"
  [cm]
  (insert cm "\n")
  (.execCommand cm "goLineDown")
  (indent-current-line cm)
  (.execCommand cm "goLineUp")
  (create-line-comment cm))

(defn in-code?
  "returns true if token is in the middle of code. assumes you've already ruled
  out comments."
  [cm]
  (let [{:keys [type start end ch]} (get-info cm)]
    (and (< start ch)
         (< ch end)
         (not (nil? type)))))

(defn in-whitespace?
  "returns true if token is to the right of whitespace"
  [cm]
  (-> cm get-type nil?))

(defn code-to-left?
  "returns true if there's any code to the left of cursor. assumes you've
  already ruled out comments so only looks for non nil tokens"
  [cm]
  (let [cur   (cursor cm)
        toks  (.getLineTokens cm (.-line cur))
        ch    (.-ch cur)
        code (map #(and (not (nil? (.-type %)))
                        (or (<= (.-end %) ch)
                            (and (< (.-start %) ch)
                                 (< ch (.-end %)))))
                  toks)]
    (and (seq toks) ; the line is not empty
         (some true? code)))) ; there's one token that contains code to the left

(defn ^:export comment-dwim [cm]
  (cond
    (selection-completely-whitespace? cm)        :do-nothing
    (selection-completely-non-code? cm)          (uncomment-selection cm)
    (.somethingSelected cm)                      (comment-selection cm)
    (line-ends-with-comment? cm)                 (go-to-comment-and-indent cm)
    (betw-code-and-line-end? cm)                 (create-comment-at-end cm)
    (in-code? cm)                                (create-comment-at-end cm)
    (in-string? cm)                              (create-comment-at-end cm)
    (line-is-whitespace? cm)                     (create-line-comment cm)
    (and (code-to-left? cm) (in-whitespace? cm)) (new-line-and-comment cm)
    (in-whitespace? cm)                          (insert-line-comment-here cm)
    :default                                     :do-nothing))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-newline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; seems like code mirror behaves as desired already

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn backspace
  "delete 1 or n char to left"
  ([cm] (backspace cm 1))
  ([cm n]
   (let [-n #(- % n)
         cur (cursor cm)
         cur0 (->> cur (index cm) -n (cursor cm))]
     (.replaceRange cm "" cur0 cur))))

(defn right-cur-would-be-whitespace?
  "true if this position would be whitespace if we pressed the spacebar."
  [cm cur right-cur]
  (let [original-cur (cursor cm)
        _ (insert cm " " 0 cur)
        answer (nil? (get-type cm right-cur))]
    (backspace cm)
    (.setCursor cm original-cur)
    answer))

(defn closing-delim?
  "returns true for closing brackets and for closing double-quotes"
  [cm cur]
  (let [{:keys [string type left-char right-cur]} (get-info cm cur)]
    ;;(println "closing delim?" type string left-char)
    (or (and (is-bracket-type? type) (closer? left-char))
        (end-of-a-string? cm cur)
        (and (= type "string")
             (= "\"" left-char)
             ;; at this point, we could be just inside the start of a string.
             ;; if we check the type at the position to the right, this could
             ;; trick us: "X""hello" ... one way to be absolutely sure we're
             ;; at the end of a string is to add a space temporarily and see
             ;; if code mirror says its type is 'null' or 'string'.
             (right-cur-would-be-whitespace? cm cur right-cur)))))

(defn opening-doublequote?
  "returns true if cur is just to the right of an opening doublequote"
  ([cm cur]
   (let [{:keys [type left-char right-cur]} (get-info cm cur)]
     (opening-doublequote? cm type left-char right-cur)))
  ([cm type left-char right-cur]
   (and (= type "string")
        (= "\"" left-char)
        right-cur
        (= "string" (get-type cm right-cur)))))

(defn closing-doublequote?
  "returns true if cur is just to the right of a closing doublequote"
  [cm cur]
  (let [{:keys [type left-char right-cur]} (get-info cm cur)
        right-type (get-type cm right-cur)]
    (and (= type "string")
         (= "\"" left-char)
         (not= right-type "string"))))

(defn opening-delim?
  "returns true for opening brackets and for opening double-quotes"
  [cm cur]
  (let [{:keys [string type left-char right-cur]} (get-info cm cur)]
    (or (and (is-bracket-type? type) (opener? left-char))
        (opening-doublequote? cm type left-char right-cur))))

(defn opening-delim-for-empty-pair?
  "returns true for an opening bracket of an empty pair ()"
  [cm cur]
  (let [{:keys [left-char right-char right-cur]} (get-info cm cur)]
    (and (opening-delim? cm cur)
         right-cur
         (closing-delim? cm right-cur)
         (pair? left-char right-char))))

(defn opening-delim-for-non-empty-pair?
  "returns true for an opening bracket of a pair that contains one or more
  chars."
  [cm]
  (let [{:keys [left-char right-char cur]} (get-info cm)]
    (and (opening-delim? cm cur)
         (not (pair? left-char right-char)))))

(defn move
  "moves the cursor by 'offset' places, negative for left. returns the cursor."
  [cm offset]
  (->> cm index (+ offset) (cursor cm) (.setCursor cm))
  (cursor cm))

(defn delete
  "delete 1 or n char to right"
  ([cm] (delete cm 1))
  ([cm n] (let [+n #(+ % n)
                cur (cursor cm)
                cur2 (->> cur (index cm) +n (cursor cm))]
            (.replaceRange cm "" cur cur2))))

(defn whitespace?
  "returns true if cursor indicates whitespace"
  [cm cur]
  (let [info (get-info cm cur)]
    (and (not (nil? info))
         (nil? (:type info)))))

(defn bracket?
  "true if cursor info indicates opening/closing bracket or quote"
  [cm cur]
  (let [{:keys [type left-char] :as info} (get-info cm cur)]
    (or (is-bracket-type? type)
        (and (= "string" type)
             (= "\"" left-char)))))

(defn select-pair
  "assumes a pair of brackets surround the cursor. selects the pair."
  [cm]
  (let [i  (->> cm cursor (index cm))
        c1 (->> i dec (cursor cm))
        c2 (->> i inc (cursor cm))]
    (.setSelection cm c1 c2)))

(defn delete-selection [cm] (.replaceSelection cm ""))

(defn delete-pair
  "assumes a pair of brackets surround the cursor. deletes the pair."
  [cm]
  (backspace cm)
  (delete cm))

(defn move-right [cm] (move cm  1))
(defn move-left  [cm] (move cm -1))

(defn ^:export forward-delete
  "paredit-forward-delete exposed for keymap"
  [cm]
  (let [{:keys [cur right-cur] :as info} (get-info cm)]
    (cond
      (.somethingSelected cm)                (delete-selection cm)
      (whitespace? cm right-cur)             (delete cm)
      (not (bracket? cm right-cur))          (delete cm)
      (opening-delim? cm right-cur)          (move-right cm)
      (opening-delim-for-empty-pair? cm cur) (delete-pair cm)
      :default                               :do-nothing)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward-delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export backward-delete
  "paredit backward delete exposed for keymap"
  [cm]
  (let [cur (cursor cm)]
    (cond
      (.somethingSelected cm)                (delete-selection cm)
      (in-escaped-char? cm cur)              (delete-pair cm)
      (escaped-char-to-left? cm cur)         (backspace cm 2)
      (opening-delim-for-non-empty-pair? cm) :do-nothing
      (opening-delim-for-empty-pair? cm cur) (delete-pair cm)
      (closing-delim? cm cur)                (move-left cm)
      :default                               (backspace cm))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-kill
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn in-regular-string?
  "returns true if token is in the middle of a string."
  [cm cur]
  (or (opening-doublequote? cm cur)
      (and (= "string" (get-type cm cur))
           (not (closing-doublequote? cm cur)))))

(defn str-ends-on-another-line?
  "true if these values are from a string token that ends on another line"
  [type string]
  (and (= "string" type)
       (not= "\"" (last string))))

(defn go-to-end-of-string
  "moves cursor to end of the string you're in (but still inside the
  closing doublequote). assumes you're in a string. the end could be
  on a different line from where you start"
  ([cm] (go-to-end-of-string cm (cursor cm)))
  ([cm cur]
   (let [{:keys [left-char right-cur type string ch end]} (get-info cm cur)]
     (cond

       (nil? type)
       (go-to-end-of-string cm right-cur)

       (str-ends-on-another-line? type string)
       (do (move-to-end-of-line cm cur), (move cm 2), (go-to-end-of-string cm))

       (opening-doublequote? cm type left-char right-cur)
       (do (move cm 1), (go-to-end-of-string cm))

       (and (= "string" type))
       (move cm (- end ch 1 ))

       :default cur))))

(defn select-rest-of-string
  "assumes you are in a string."
  [cm]
  (let [c1 (cursor cm)
        c2 (go-to-end-of-string cm c1)]
    (.setSelection cm c1 c2)))

(defn betw-code-and-comment?
  "true if code is to the left and whitespace* comment* is to the right."
  [cm cur]
  (when cur
    (let [toks  (.getLineTokens cm (.-line cur))
          ch    (.-ch cur)
          tests (map #(or (<= (.-end %) ch)
                          (or (nil? (.-type %))
                              (= "comment" (.-type %)))) toks)]
      (and (seq toks) ; the line is not empty
           (every? true? tests) ; there's only junk to the right
           (some #(not (nil? (.-type %))) toks)))))

(defn rest-of-siblings
  [cm]
  (let [c1 (cursor cm)
        parent-closer (skip cm parent-closer-sp)
        c2 (when parent-closer (cursor cm (dec (index cm parent-closer))))]
    [c1 c2]))

(defn select-rest-of-siblings
  [cm]
  (let [[c1 c2] (rest-of-siblings cm)c1 (cursor cm)]
    (when c2 (.setSelection cm c1 c2))))

(defn kill-from-to [cm i j]
  (let [cur (cursor cm i)]
    (CodeMirror.emacs.kill cm cur (cursor cm j))
    (.setCursor cm cur)))

(defn kill-region [cm]
  (let [first-sel (-> cm .listSelections first)
        anchor (.-anchor first-sel)
        head   (.-head   first-sel)]
    (CodeMirror.emacs.kill cm anchor head)))

(defn kill-pair
  "assumes a pair of brackets surround the cursor. deletes the pair."
  [cm]
  (select-pair cm)
  (kill-region cm))

(defn kill-rest-of-string [cm]
  (select-rest-of-string cm)
  (kill-region cm))

(defn kill-rest-of-line [cm]
  (select-rest-of-line cm)
  (kill-region cm))

(defn kill-rest-of-siblings [cm]
  (select-rest-of-siblings cm)
  (kill-region cm))

(defn kill-next-sibling
  "kills the next sibling to the right of the cursor"
  [cm]
  (let [from (cursor cm)
        mid (end-of-next-sibling cm from)
        to (if (betw-code-and-comment? cm mid) (last-cur cm mid) mid)]
    (when to
      (.setSelection cm from to)
      (kill-region cm))))

(defn ^:export kill
  "paredit kill exposed for keymap."
  [cm]
  (let [cur (cursor cm)]
    (cond
      (.somethingSelected cm)         (kill-region cm)
      (in-regular-string? cm cur)     (kill-rest-of-string cm)
      (betw-code-and-comment? cm cur) (kill-rest-of-line cm)
      (in-escaped-char? cm cur)       (kill-pair cm)
      (code-to-left? cm)              (kill-rest-of-siblings cm)
      :default                        (kill-next-sibling cm))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-kill-word M-d
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn comment?
  [cm cur]
  (= "comment" (get-type cm cur)))

(defn start-of-comment?
  "true if block cursor is on the first ; of a line comment"
  [cm cur]
  (let [{:keys [type right-cur]} (get-info cm cur)
        right-type (get-type cm right-cur)]
    (and (not= "comment" type)
         (= "comment right-type"))))

(defn idx-of-next [cm i chars member max]
  (let [{:keys [right-char]} (get-info cm (cursor cm i))]
    (cond
      (= i max), (guard)
      (= member (contains? chars right-char)), i
      :default, (fn [] (idx-of-next cm (inc i) chars member max)))))

(defn index-of-next [cm i chars]
  (trampoline idx-of-next cm i chars true (char-count cm)))

(defn index-of-next-non [cm i chars]
  (trampoline idx-of-next cm i chars false (char-count cm)))

(def non-word-chars (set "(){}[]|&; \n"))

(def comment-start (set "; "))
(def semicolons #{";"})
(def comment-whitespace #{" " (str \tab)})

(defn end-of-next-word
  "assumes i is in a comment or a string. returns the i at the end of
  the next word (going to the right) in this comment/string"
  [cm i]
  (let [{:keys [ch start string]} (get-info cm (cursor cm i))
        tail (subs string (- ch start))
        word (re-find #"^\s*[\S]*" tail)
        length (count word)
        quote (if (str/ends-with? word "\"") -1 0)]
    (+ i length quote)))

(defn start-of-prev-word
  "assumes i is in a comment or a string. returns the i at the start of
  the prev word (going to the left) in this comment/string"
  [cm i]
  (let [{:keys [ch start string]} (get-info cm (cursor cm i))
        head (subs string 0 (- ch start))
        last-word (re-find #"[\S]*\s*$" head)
        length (count last-word)
        quote (if (str/ends-with? last-word "\"") 1 0)]
    (- i length quote)))

(defn kill-next-word
  "assumes i is in a comment or a string. kills text from i to the end
  of the next word in this comment/string"
  [cm i]
  (kill-from-to cm i (end-of-next-word cm (inc i)))
  (.setCursor cm (cursor cm i)))

(defn fwd-kill-word
  "trampoline helper for forward-kill-word. 'mark' is the index to start killing
  from. 'i' is the index we're inspecting. 'n' is how many calls remaining that
  we'll support before stopping because of a suspected infinite loop. first call
  can put the count of characters in this cm instance."
  [cm mark i n]
  (let [m (dec n), j (inc i), cur (cursor cm i), right-cur (cursor cm j)]
    (cond
      (neg? n)
      (guard)

      (eof? cm right-cur)
      :do-nothing

      (whitespace? cm right-cur)
      #(fwd-kill-word cm mark (token-end-index cm j) m)

      (start-of-a-string? cm right-cur)
      #(fwd-kill-word cm j j m)

      (in-regular-string? cm right-cur)
      (kill-next-word cm mark)

      (opening-delim? cm right-cur)
      #(fwd-kill-word cm j j m)

      (closing-delim? cm right-cur)
      #(fwd-kill-word cm j j m)

      (at-a-word? cm right-cur)
      (kill-from-to cm mark (token-end-index cm j))

      (start-of-comment? cm cur)
      (let [j (index-of-next-non cm i semicolons)]
        #(fwd-kill-word cm j j m))

      (comment? cm right-cur)
      (kill-next-word cm mark)

      :else
      (println "unhandled"))))

(defn ^:export forward-kill-word
  "paredit forward-kill-word exposed for keymap."
  [cm]
  (let [i (index cm)]
    (trampoline fwd-kill-word cm i i (char-count cm))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward-kill-word
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-of-token-at
  [cm i]
  (let [{:keys [ch start]} (get-info cm (cursor cm i))]
    (- i (- ch start))))

(defn kill-prev-word-in-comment
  "assumes i is in a comment. kills text from i to the beginning of the previous
  word in this comment"
  [cm i]
  (let [{:keys [ch start string]} (get-info cm (cursor cm i))
        cur-offset-in-string (- ch start)
        head (subs string 0 cur-offset-in-string)
        tail (subs string cur-offset-in-string)
        word (re-find #"\S*\s*$" head)
        length (count word)]
    (kill-from-to cm (- i length) i)
    (.setCursor cm (cursor cm (- i length)))))

(defn beginning-of-line?
  [cm cur]
  (let [{:keys [start end type] :as info} (get-info cm cur)]
    (and (not (nil? info))
         (nil? type)
         (= start end 0))))

(defn bkwd-kill-skippable-comment-char?
  [cm cur]
  (let [{:keys [type left-char] :as info} (get-info cm cur)]
    (and (not (nil? info))
         (= "comment" type)
         (re-matches #"\s|;" left-char))))

(defn bkwd-kill-word
  "trampoline helper for backward-kill-word. 'mark' is the index to start
  killing from. 'i' is the index we're inspecting. 'n' is how many more calls
  we'll entertain before stopping because we suspect an infinite loop. first
  call can use char count for 'n'."
  [cm mark i n]
  (let [h (dec i), m (dec n), cur (cursor cm i)]
    (cond
      (neg? n)
      (guard)

      (bof? cm cur)
      :do-nothing

      (beginning-of-line? cm cur)
      #(bkwd-kill-word cm h h m)

      (whitespace? cm cur)
      #(bkwd-kill-word cm mark (start-of-token-at cm i) m)

      (opening-delim? cm cur)
      #(bkwd-kill-word cm h h m)

      (closing-delim? cm cur)
      #(bkwd-kill-word cm h h m)

      (at-a-word? cm cur)
      (kill-from-to cm (start-of-token-at cm i) mark)

      (start-of-comment? cm cur)
      (let [j (index-of-next-non cm i semicolons)]
        #(fwd-kill-word cm j j m))

      (bkwd-kill-skippable-comment-char? cm cur)
      #(bkwd-kill-word cm mark h m)

      (comment? cm cur)
      (kill-prev-word-in-comment cm mark)

      :else
      (println "unhandled"))))

(defn ^:export backward-kill-word
  "paredit backward-kill-word exposed for keymap."
  [cm]
  (let [i (index cm)]
    (trampoline bkwd-kill-word cm i i (char-count cm))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fwd
  "trampoline helper for forward. 'i' is the index we're inspecting. 'n' is how
  many more calls we'll entertain before suspecting an infinite loop. first call
  can pass in char count."
  [cm i n]
  (let [j (inc i), m (dec n), cur (cursor cm i), right-cur (cursor cm j)]
    (cond
      (neg? n)
      (guard)

      (nil? right-cur)
      :do-nothing

      (eof? cm right-cur)
      :do-nothing

      (whitespace? cm right-cur)
      #(fwd cm j m)

      (opening-delim? cm right-cur)
      (.setCursor cm (end-of-next-sibling cm cur))

      (closing-delim? cm right-cur)
      (.setCursor cm right-cur)

      (at-a-word? cm right-cur)
      (.setCursor cm (cursor cm (token-end-index cm j)))

      (comment? cm right-cur)
      #(fwd cm (token-end-index cm j) m)

      (in-string? cm right-cur)
      (.setCursor cm (cursor cm (end-of-next-word cm j)))

      :else
      (println "unhandled"))))

(defn ^:export forward
  "paredit forward exposed for keymap. find the first thing that isn't
  whitespace or comment. if it is a closing bracket, step past it. otherwise
  skip over the thing."
  [cm]
  (trampoline fwd cm (index cm) (char-count cm)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bkwd
  "trampoline helper for backward. 'i' is the index we're inspecting. 'n' is
  number of remaining calls before we suspect an infinite loop"
  [cm i n]
  (let [h (dec i), m (dec n), cur (cursor cm i)]
    (cond
      (neg? n)
      (guard)

      (nil? cur)
      :do-nothing

      (bof? cm cur)
      (.setCursor cm (cursor cm h))

      (whitespace? cm cur)
      #(bkwd cm h m)

      (opening-delim? cm cur)
      (.setCursor cm (cursor cm h))

      (closing-delim? cm cur)
      (.setCursor cm (start-of-prev-sibling cm cur))

      (at-a-word? cm cur)
      (.setCursor cm (start-of-prev-sibling cm cur))

      (comment? cm cur)
      #(bkwd cm (start-of-prev-sibling cm cur) m)

      (in-string? cm cur)
      (.setCursor cm (cursor cm (start-of-prev-word cm h)))

      :else
      (println "unhandled"))))

(defn ^:export backward
  "paredit backward exposed for keymap."
  [cm]
  (trampoline bkwd cm (index cm) (char-count cm)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-up
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn forward-up-cur
  "get cursor corresponding to paredit forward up"
  ([cm] (forward-up-cur cm (cursor cm)))
  ([cm cur]
   (cond
     (nil? cur), nil

     (and (in-string? cm cur) (not (end-of-a-string? cm cur)))
     (token-end cm cur)

     :default, (skip cm parent-closer-sp))))

(defn ^:export forward-up
  "paredit forward-up exposed for keymap."
  ([cm] (forward-up cm (cursor cm)))
  ([cm cur]
   (when-let [cur' (forward-up-cur cm cur)]
     (.setCursor cm cur'))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward-up
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn backward-up-cur
  "get cursor corresponding to paredit backward up"
  ([cm] (backward-up-cur cm (cursor cm)))
  ([cm cur]
   (start-of-prev-sibling cm (forward-up-cur cm cur))))

(defn ^:export backward-up
  "paredit backward-up exposed for keymap."
  ([cm] (backward-up cm (cursor cm)))
  ([cm cur]
   (when-let [cur' (backward-up-cur cm cur)]
     (.setCursor cm cur'))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-wrap-round
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn end-of-this
  "go to the end of the current thing, whether it be a string or a word of code"
  [cm cur]
  (if (in-string? cm cur)
    (token-end cm cur)
    (end-of-next-sibling cm cur)))

(defn ^:export wrap-round
  "paredit wrap-round exposed for keymap."
  ([cm] (wrap-round cm (cursor cm)))
  ([cm cur]
   (let [cur-close (end-of-this cm cur)
         cur-open (start-of-prev-sibling cm cur-close)
         i (inc (index cm cur-open))
         text (.getRange cm cur-open cur-close)
         text' (str "(" text ")")]
     (.replaceRange cm text' cur-open cur-close)
     (.setCursor cm (cursor cm i)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-splice-sexp M-s
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export splice-sexp
  "paredit splice-sexp exposed for keymap. unlike emacs' version, this does not
  splice a string by dropping its double-quotes."
  ([cm] (splice-sexp cm (cursor cm)))
  ([cm cur]
   (let [i (dec (index cm))
         cur-close (skip cm parent-closer-sp)
         cur-open (start-of-prev-sibling cm cur-close)
         text' (when cur-open
                 (.getRange cm
                            (cursor cm (inc (index cm cur-open)))
                            (cursor cm (dec (index cm cur-close)))))]
     (when text'
       (.replaceRange cm text' cur-open cur-close)
       (.setCursor cm (cursor cm i))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-splice-sexp-killing-backward M-<up>
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export splice-sexp-killing-backward
  "paredit splice-sexp-killing-backward exposed for keymap. like emacs' version,
  this doesn't actually kill to the clipboard. it just deletes. but unlink
  emacs, this does not splice a string by dropping its double-quotes."
  ([cm] (splice-sexp-killing-backward cm (cursor cm)))
  ([cm cur]
   (if (in-string? cm cur) (backward-up cm cur))
   (let [cur' (cursor cm)
         cur-close (skip cm parent-closer-sp)
         cur-open (start-of-prev-sibling cm cur-close)
         text' (when cur-close
                 (.getRange cm cur' (cursor cm (dec (index cm cur-close)))))]
     (when text'
       (.replaceRange cm text' cur-open cur-close)
       (.setCursor cm cur-open)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-splice-sexp-killing-forward M-<down>
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export splice-sexp-killing-forward
  "paredit splice-sexp-killing-forward exposed for keymap. like emacs' version,
  this doesn't actually kill to the clipboard. it just deletes. but unlink
  emacs, this does not splice a string by dropping its double-quotes."
  ([cm] (splice-sexp-killing-forward cm (cursor cm)))
  ([cm cur]
   (if (in-string? cm cur) (forward-up cm cur))
   (let [cur' (cursor cm)
         final-cur (cursor cm (dec (index cm cur')))
         cur-close (skip cm parent-closer-sp)
         cur-open (start-of-prev-sibling cm cur-close)
         keep-from (when cur-open (cursor cm (inc (index cm cur-open))) )
         text (when keep-from (.getRange cm cur-open cur-close))
         text' (when keep-from (.getRange cm keep-from cur'))]
     (when text'
       (.replaceRange cm text' cur-open cur-close)
       (.setCursor cm final-cur)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-raise-sexp M-r
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export raise-sexp
  "paredit raise-sexp exposed for keymap."
  ([cm] (raise-sexp cm (cursor cm)))
  ([cm cur]
   (if (in-string? cm cur) (backward-up cm cur))
   (let [c1 (cursor cm)
         c2 (end-of-next-sibling cm c1)
         text (when c2 (.getRange cm c1 c2))
         cur-close (when text (skip cm parent-closer-sp))
         cur-open (when cur-close (start-of-prev-sibling cm cur-close))]
     (when cur-open
       (.replaceRange cm text cur-open cur-close)
       (.setCursor cm cur-open)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-slurp-sexp C-), C-<right>
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fwd-slurp
  "trampoline-able that looks for an ancestor closing bracket (parent,
  grandparent, etc) that has a sibling to slurp. returns a vector of the cur to
  the right of such a bracket, the cur to the right of the sibling that will be
  slurped, the string of the bracket to move. nil if there is no such anscestor
  that can slurp."
  [cm cur n]
  (when (>= n 0)
    (let [parent (skip cm parent-closer-sp cur)
          sibling (end-of-next-sibling cm parent)]
      (if sibling
        [parent sibling (get-string cm parent)]
        (fn [] (fwd-slurp cm parent (dec n)))))))

(defn ^:export forward-slurp-sexp
  "paredit forward-slurp-sexp exposed for keymap."
  ([cm] (forward-slurp-sexp cm (cursor cm)))
  ([cm cur]
   (when-let [[parent sibling bracket]
              (trampoline fwd-slurp cm cur (char-count cm))]
     (insert cm bracket 0 sibling);; put bracket in new spot
     (.replaceRange cm "" (cursor cm (- (index cm parent) (count bracket)))
                    parent));; remove bracket from old spot
   (.setCursor cm cur)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-down C-M-d
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fwd-down
  "trampoline-able that looks for the cursor where we'd be if we went forward
  and then down into the next sibling that is available. nil if there is no
  sibling to enter."
  [cm cur n]
  (cond
    (<= n 0), nil
    (nil? cur), nil
    (opening-delim? cm cur), cur
    :default, (when-let [cur' (token-end cm cur 1)]
                (fn [] (fwd-down cm cur' (dec n))))))

(defn forward-down-cur
  ([cm] (forward-down-cur cm (cursor cm)))
  ([cm cur]
   (trampoline fwd-down cm cur (char-count cm))))

(defn ^:export forward-down
  ([cm] (forward-down cm (cursor cm)))
  ([cm cur]
   (when-let [cur' (forward-down-cur cm cur)]
     (.setCursor cm cur'))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward-down C-M-p
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bkwd-down
  "trampoline-able that looks for the cursor where we'd be if we went backward
  and then down into the prev sibling that is available. nil if there is no
  sibling to enter."
  [cm cur n]
  (let [{:keys [left-cur i start ch bof]} (get-info cm cur)]
    (cond
      (<= n 0), (guard)
      (closing-delim? cm cur), left-cur
      bof, nil
      (zero? ch), (fn [] (bkwd-down cm (cursor cm (dec i)) (dec n)))
      :default, (fn [] (bkwd-down cm (cursor cm (- i (- ch start))) (dec n))))))

(defn ^:export backward-down
  ([cm] (backward-down cm (cursor cm)))
  ([cm cur]
   (when-let [cur' (trampoline bkwd-down cm cur (char-count cm))]
     (.setCursor cm cur'))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward-slurp-sexp C-), C-<right>
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bkwd-slurp
  "trampolin-able that looks for an ancestor opening bracket (parent,
  grandparent, etc) that has a sibling to slurp. returns a vector of the cur to
  the left of such a bracket, the cur to the left of the sibling that will be
  slurped, the string of the bracket to move. nil if there is no such anscestor
  that can slurp."
  [cm cur n]
  (when (>= n 0)
    (let [ending (skip cm parent-closer-sp cur)
          parent (start-of-prev-sibling cm ending)
          sibling (start-of-prev-sibling cm parent)
          bracket-cur (forward-down-cur cm parent)]
      (if (and (not (nil? sibling)) (not (nil? bracket-cur)))
        [parent sibling (get-string cm bracket-cur)]
        (fn [] (bkwd-slurp cm parent (dec n)))))))

(defn ^:export backward-slurp-sexp
  "paredit backward-slurp-sexp exposed for keymap."
  ([cm] (backward-slurp-sexp cm (cursor cm)))
  ([cm cur]
   (let [i (index cm cur)] ;; line,ch may change but index will not.
     (when-let [[parent sibling bracket]
                (trampoline bkwd-slurp cm cur (char-count cm))]
       (.replaceRange cm "" parent
                      (cursor cm (+ (index cm parent) (count bracket))))
       (insert cm bracket 0 sibling))
     (.setCursor cm (cursor cm i)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-barf-sexp C-\} C-<left>
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fwd-barf
  "trampoline-able that looks for an ancestor closing bracket (parent,
  grandparent, etc) that has a sibling to barf. returns a vector of
  the cur to the right of such a bracket, the cur at the bracket, the
  cur where the bracket should go, the text of the bracket, and
  whether the operation causes the cursor to be moved. nil if there is
  no such anscestor that can barf"
  [cm cur n]
  (when (>= n 0)
    (let [parent (skip cm parent-closer-sp cur)
          inside (cursor cm (dec (index cm parent)))
          sibling (start-of-prev-sibling cm inside)
          ;; prevsib: end of prev sibling if there is one:
          prevsib (end-of-next-sibling cm (start-of-prev-sibling cm sibling))
          ;; bracket-cur: where the new bracket should go:
          bracket-cur (or prevsib
                        (forward-down-cur cm (backward-up-cur cm sibling)))
          ;; whether the cursor needs to change:
          moved (and bracket-cur (< (index cm bracket-cur) (index cm cur)))
          ;; text of the bracket, e.g. ")"
          bracket (when parent
                    (if moved
                      (str (get-string cm parent) " ")
                      (get-string cm parent)))]
      (cond
        (nil? parent) nil
        (nil? bracket-cur) (fn [] (fwd-barf cm parent (dec n)))
        :default [parent inside bracket-cur bracket moved]))))

(defn ^:export forward-barf-sexp
  "paredit forward-barf-sexp exposed for keymap."
  ([cm] (forward-barf-sexp cm (cursor cm)))
  ([cm cur]
   (if-let [[parent inside sibling bracket moved]
            (trampoline fwd-barf cm cur (char-count cm))]
     (do #_(js/console.log parent inside sibling bracket moved)
         (.replaceRange cm "" inside parent)
         (insert cm bracket 0 sibling)
         (if moved
           (.setCursor cm (cursor cm (+ (index cm cur) (count bracket))))
           (.setCursor cm cur)))
     (.setCursor cm cur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backard-barf-sexp C-{, C-M-<right>, Esc C-<right>
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bkwd-barf
  "trampoline-able that looks for an ancestor opening bracket (parent,
  grandparent, etc) that has a sibling to barf. returns... . nil if
  there is no such anscestor that can barf"
  [cm cur n]
  (when (>= n 0)
    (let [outside (backward-up-cur cm cur)
          inside (forward-down-cur cm outside)
          end-of-barfed-sexp (end-of-next-sibling cm inside)
          end-of-new-first-sib (end-of-next-sibling cm end-of-barfed-sexp)
          bracket-cur (start-of-prev-sibling cm end-of-new-first-sib)
          bracket-text (get-string cm inside)
          moved (and bracket-cur (< (index cm cur) (index cm bracket-cur)))]
      (cond
       (nil? outside) nil
       (nil? end-of-barfed-sexp) (fn [] (bkwd-barf cm outside (dec n)))
       :default [outside inside bracket-cur bracket-text moved]))))

(defn ^:export backward-barf-sexp
  "paredit backward-barf-sexp exposed for keymap."
  ([cm] (backward-barf-sexp cm (cursor cm)))
  ([cm cur]
   (if-let [[outside inside bracket-cur bracket-text moved]
            (trampoline bkwd-barf cm cur (char-count cm))]
     (do (insert cm bracket-text 0 bracket-cur)
         (.replaceRange cm "" outside inside)
         (if moved
           (.setCursor cm (cursor cm (- (index cm cur) (count bracket-text))))
           (.setCursor cm cur)))
     (.setCursor cm cur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-split-sexp M-S
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn split-form
  "split sexp for (forms like this)"
  [cm cur]
  (let [close-cur (skip cm parent-closer-sp cur)
        close-bracket (get-string cm close-cur)
        open-cur (start-of-prev-sibling cm close-cur)
        open-bracket (get-string cm (cursor cm (inc (index cm open-cur))))]
    (when (and (not (nil? open-bracket)) (not (nil? close-bracket)))
      (.setCursor cm cur)

      (let [offset (if (in-whitespace? cm)
                     1
                     (do (insert cm " ")
                         (just-one-space cm (cursor cm) false)
                         0))
            cur' (cursor cm)
            i' (+ (index cm cur') offset)
            prev-sib (start-of-prev-sibling cm cur')
            prev-sib-end (end-of-next-sibling cm prev-sib)
            next-sib (end-of-next-sibling cm cur)
            next-sib-start (start-of-prev-sibling cm next-sib)]
        (if (nil? next-sib-start)
          (insert cm open-bracket)
          (insert cm open-bracket 0 next-sib-start))
        (if (nil? prev-sib-end)
          (do (move-left cm)
              (insert cm close-bracket))
          (insert cm close-bracket 0 prev-sib-end))
        (.setCursor cm (cursor cm i'))))))


(defn split-string
  "split sexp for \"strings like this\""
  [cm cur]
  (let [open-quote-i (index-of-next-non cm (index cm cur) " ")]
    (.replaceRange cm "\" \"" cur (cursor cm open-quote-i))
    (move-left cm)
    (move-left cm)))

(defn ^:export split-sexp
  "paredit split-sexp exposed for keymap."
  ([cm] (split-sexp cm (cursor cm)))
  ([cm cur]
   (if (in-string? cm cur)
     (split-string cm cur)
     (split-form cm cur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-join-sexps M-J
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export join-sexps
  "paredit join-sexps exposed for keymap."
  ([cm] (join-sexps cm (cursor cm)))
  ([cm cur]
   (let [left-sib (start-of-prev-sibling cm cur)
         close (end-of-next-sibling cm left-sib)
         right-sib (end-of-next-sibling cm cur)
         open (start-of-prev-sibling cm right-sib)
         open-right (when open (cursor cm (inc (index cm open))))
         close-char (get-string cm close)
         open-char (get-string cm open-right)]
     (if (and (not (nil? open))
              (not (nil? close))
              (pair? open-char close-char))
       (do (.setCursor cm open)
           (delete cm)
           (.setCursor cm close)
           (backspace cm)
           (.setCursor cm (if (= (.-line open) (.-line close))
                            (cursor cm (dec (index cm cur)))
                            cur)))
       (.setCursor cm cur)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-reindent-defun M-q
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn top-most-opener-candidate
  "trampoline-able that looks for the top-most opening bracket for the specified
  location. returns the current cursor if there is no such anscestor"
  [cm cur n]
  (when (>= n 0)
    (if-let [parent (backward-up-cur cm cur)]
      (fn [] (top-most-opener-candidate cm parent (dec n)))
      cur)))

(defn top-most-opener
  "get the top most opening bracket for the specified location. nil if
  there is no such bracket."
  ([cm] (top-most-opener cm (cursor cm)))
  ([cm cur] (let [candidate (top-most-opener-candidate cm cur (char-count cm))]
              (when (not= candidate cur) candidate))))

(defn ^:export reindent-defun
  "paredit reindent-defun exposed for keymap."
  ([cm] (reindent-defun cm (cursor cm)))
  ([cm cur]
   (let [open (trampoline top-most-opener cm cur)
         close (end-of-next-sibling cm open)
         open-line (when open (.-line open))
         line-offset (when open (- (.-line cur) open-line))
         line-len (count (.getLine cm (.-line cur)))
         ch (.-ch cur)]
     (when (and (not (nil? open)) (not (nil? close)))
       (indent-lines cm (.-line open) (.-line close))
       (repeatedly line-offset (.execCommand cm "goLineDown"))
       (.execCommand cm "goLineStart")
       (.setCursor
        cm
        (cursor cm (+ (index cm)
                      ch
                      (- (count (.getLine cm (.-line (cursor cm))))
                         line-len))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-forward-sexp
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export forward-sexp
  "forward-sexp exposed for keymap. seems part of emacs and not part
  of paredit itself. but including it here since this will be used in
  things other than emacs itself."
  ([cm] (forward-sexp cm (cursor cm)))
  ([cm cur]
   (when-let [cur' (end-of-next-sibling cm cur)]
     (.setCursor cm cur'))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; paredit-backward-sexp
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:export backward-sexp
  "backward-sexp exposed for keymap. seems part of emacs and not part
  of paredit itself. but including it here since this will be used in
  things other than emacs itself."
  ([cm] (backward-sexp cm (cursor cm)))
  ([cm cur]
   (when-let [cur' (start-of-prev-sibling cm cur)]
     (.setCursor cm cur'))))
