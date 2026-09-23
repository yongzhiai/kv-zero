// Package store implements a small, concurrency-safe key-value store with
// optional JSON snapshot persistence. It is the core of kv-zero.
package store

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"sync"
)

// ErrNotFound is returned when a key does not exist in the store.
var ErrNotFound = errors.New("key not found")

// Store is an in-memory key-value store safe for concurrent use.
//
// When a snapshot path is configured, the full data set can be flushed to disk
// as JSON and reloaded on startup, giving the store durability across restarts
// without pulling in an external database.
type Store struct {
	mu   sync.RWMutex
	data map[string]string
	path string
}

// New returns an empty in-memory store with no persistence configured.
func New() *Store {
	return &Store{data: make(map[string]string)}
}

// Open returns a store backed by the JSON snapshot at path. If the file exists
// its contents are loaded; a missing file simply starts an empty store.
func Open(path string) (*Store, error) {
	s := &Store{data: make(map[string]string), path: path}
	if err := s.load(); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Store) load() error {
	if s.path == "" {
		return nil
	}
	raw, err := os.ReadFile(s.path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil
		}
		return err
	}
	if len(raw) == 0 {
		return nil
	}
	loaded := make(map[string]string)
	if err := json.Unmarshal(raw, &loaded); err != nil {
		return err
	}
	s.data = loaded
	return nil
}

// Get returns the value stored for key, or ErrNotFound if it is absent.
func (s *Store) Get(key string) (string, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	v, ok := s.data[key]
	if !ok {
		return "", ErrNotFound
	}
	return v, nil
}

// Set stores value under key, overwriting any previous value.
func (s *Store) Set(key, value string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data[key] = value
}

// Delete removes key. It returns ErrNotFound if the key was not present.
func (s *Store) Delete(key string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.data[key]; !ok {
		return ErrNotFound
	}
	delete(s.data, key)
	return nil
}

// Keys returns all keys currently in the store, sorted lexicographically.
func (s *Store) Keys() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	keys := make([]string, 0, len(s.data))
	for k := range s.data {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}

// Len returns the number of keys in the store.
func (s *Store) Len() int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return len(s.data)
}

// Flush writes the current data set to the configured snapshot path atomically.
// It is a no-op when no path was configured.
func (s *Store) Flush() error {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if s.path == "" {
		return nil
	}
	raw, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return err
	}
	if dir := filepath.Dir(s.path); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, raw, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}
