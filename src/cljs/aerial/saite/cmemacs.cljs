(ns aerial.saite.cmemacs
  (:require
   [cljsjs.codemirror]
   [cljsjs.codemirror.keymap.emacs]))


(def kill-region ((js->clj CodeMirror.keyMap.emacs) "Ctrl-W"))
(def kill-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-K"))
(def kill-ring-save ((js->clj CodeMirror.keyMap.emacs) "Alt-W"))

(def yank ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Y"))
(def yank-pop ((js->clj CodeMirror.keyMap.emacs) "Alt-Y"))

(def set-mark ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Space"))
(def select-all ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X H"))

(def  forward-char ((js->clj CodeMirror.keyMap.emacs) "Ctrl-F"))
(def  backward-char ((js->clj CodeMirror.keyMap.emacs) "Ctrl-B"))

(def delete-forward-char ((js->clj CodeMirror.keyMap.emacs) "Ctrl-D"))
(def delete-backward-char ((js->clj CodeMirror.keyMap.emacs) "Backspace"))

(def forward-word ((js->clj CodeMirror.keyMap.emacs) "Alt-F"))
(def backward-word ((js->clj CodeMirror.keyMap.emacs) "Alt-B"))
(def forward-kill-word ((js->clj CodeMirror.keyMap.emacs) "Alt-D"))
(def backward-kill-word ((js->clj CodeMirror.keyMap.emacs) "Alt-Backspace"))

(def next-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-N"))
(def previous-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-P"))

(def beginning-of-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-A"))
(def end-of-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-E"))

(def scroll-down ((js->clj CodeMirror.keyMap.emacs) "Alt-V"))
(def page-up scroll-down)
(def scroll-up ((js->clj CodeMirror.keyMap.emacs) "Ctrl-V"))
(def page-down scroll-up)

(def backward-paragraph ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Up"))
(def forward-paragraph ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Down"))

(def backward-sentence ((js->clj CodeMirror.keyMap.emacs) "Alt-A"))
(def forward-sentence ((js->clj CodeMirror.keyMap.emacs) "Alt-E"))
(def kill-sentence ((js->clj CodeMirror.keyMap.emacs) "Alt-K"))

(def kill-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-K"))
(def kill-back-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-Backspace"))

(def forward-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-F"))
(def backward-sexp ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-B"))

(def mark-sexp ((js->clj CodeMirror.keyMap.emacs) "Shift-Ctrl-Alt-2"))
(def transpose-sexps ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-T"))
(def backward-up-list ((js->clj CodeMirror.keyMap.emacs) "Ctrl-Alt-U"))

(def just-one-space ((js->clj CodeMirror.keyMap.emacs) "Alt-Space"))
(def open-line ((js->clj CodeMirror.keyMap.emacs) "Ctrl-O"))
(def transpose-chars ((js->clj CodeMirror.keyMap.emacs) "Ctrl-T"))

(def capitalize-word ((js->clj CodeMirror.keyMap.emacs) "Alt-C"))
(def uppercase-word ((js->clj CodeMirror.keyMap.emacs) "Alt-U"))
(def downcase-word ((js->clj CodeMirror.keyMap.emacs) "Alt-L"))

(def toggle-comment ((js->clj CodeMirror.keyMap.emacs) "Alt-;"))

(def undo ((js->clj CodeMirror.keyMap.emacs) "Ctrl-/"))
(def repeat-undo ((js->clj CodeMirror.keyMap.emacs) "Shift-Ctrl--"))

(def go-doc-start ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-,"))
(def go-doc-end ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-."))
(def goto-line ((js->clj CodeMirror.keyMap.emacs) "Alt-G G"))

(def search-forward ((js->clj CodeMirror.keyMap.emacs) "Ctrl-S"))
(def search-backward ((js->clj CodeMirror.keyMap.emacs) "Ctrl-R"))
(def query-replace ((js->clj CodeMirror.keyMap.emacs) "Shift-Alt-5"))

(def autocomplete ((js->clj CodeMirror.keyMap.emacs) "Alt-/"))
(def newline-indent ((js->clj CodeMirror.keyMap.emacs) "Enter"))
(def electric-newline-indent ((js->clj CodeMirror.keyMap.emacs) "Ctrl-J"))
(def indent-auto ((js->clj CodeMirror.keyMap.emacs) "Tab"))
(def indent-selection ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X Tab"))
(def exchange-point-mark ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X Ctrl-X"))

(def save-doc ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X Ctrl-S"))
(def open-doc ((js->clj CodeMirror.keyMap.emacs) "Ctrl-X F"))

(def keyboard-quit ((js->clj CodeMirror.keyMap.emacs) "Ctrl-G"))
