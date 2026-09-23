// Package httpapi exposes a kv-zero store over a small HTTP REST interface.
package httpapi

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/yongzhiai/kv-zero/internal/store"
)

// Server wires a store to HTTP handlers.
type Server struct {
	store *store.Store
}

// New returns a Server backed by the given store.
func New(s *store.Store) *Server {
	return &Server{store: s}
}

// Handler returns the root HTTP handler for the API.
//
// Routes:
//
//	GET    /health        -> liveness probe
//	GET    /kv            -> list all keys
//	GET    /kv/{key}      -> fetch a value
//	PUT    /kv/{key}      -> set a value (raw request body)
//	DELETE /kv/{key}      -> delete a key
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", s.handleHealth)
	mux.HandleFunc("/kv", s.handleList)
	mux.HandleFunc("/kv/", s.handleKey)
	return mux
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "keys": s.store.Len()})
}

func (s *Server) handleList(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"keys": s.store.Keys()})
}

func (s *Server) handleKey(w http.ResponseWriter, r *http.Request) {
	key := strings.TrimPrefix(r.URL.Path, "/kv/")
	if key == "" {
		writeError(w, http.StatusBadRequest, "missing key")
		return
	}

	switch r.Method {
	case http.MethodGet:
		value, err := s.store.Get(key)
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "key not found")
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"key": key, "value": value})

	case http.MethodPut:
		body, err := io.ReadAll(io.LimitReader(r.Body, 1<<20))
		if err != nil {
			writeError(w, http.StatusBadRequest, "unable to read body")
			return
		}
		s.store.Set(key, string(body))
		if err := s.store.Flush(); err != nil {
			writeError(w, http.StatusInternalServerError, "failed to persist")
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"key": key, "value": string(body)})

	case http.MethodDelete:
		err := s.store.Delete(key)
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "key not found")
			return
		}
		if err := s.store.Flush(); err != nil {
			writeError(w, http.StatusInternalServerError, "failed to persist")
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"deleted": key})

	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]any{"error": msg})
}
