package store

import (
	"path/filepath"
	"sync"
	"testing"
)

func TestSetGet(t *testing.T) {
	s := New()
	s.Set("name", "kv-zero")

	got, err := s.Get("name")
	if err != nil {
		t.Fatalf("Get returned error: %v", err)
	}
	if got != "kv-zero" {
		t.Fatalf("Get = %q, want %q", got, "kv-zero")
	}
}

func TestGetMissing(t *testing.T) {
	s := New()
	if _, err := s.Get("missing"); err != ErrNotFound {
		t.Fatalf("Get missing key err = %v, want ErrNotFound", err)
	}
}

func TestOverwrite(t *testing.T) {
	s := New()
	s.Set("k", "v1")
	s.Set("k", "v2")
	got, _ := s.Get("k")
	if got != "v2" {
		t.Fatalf("after overwrite Get = %q, want %q", got, "v2")
	}
}

func TestDelete(t *testing.T) {
	s := New()
	s.Set("k", "v")
	if err := s.Delete("k"); err != nil {
		t.Fatalf("Delete returned error: %v", err)
	}
	if _, err := s.Get("k"); err != ErrNotFound {
		t.Fatalf("after Delete Get err = %v, want ErrNotFound", err)
	}
	if err := s.Delete("k"); err != ErrNotFound {
		t.Fatalf("Delete missing key err = %v, want ErrNotFound", err)
	}
}

func TestKeysSorted(t *testing.T) {
	s := New()
	s.Set("charlie", "3")
	s.Set("alpha", "1")
	s.Set("bravo", "2")

	keys := s.Keys()
	want := []string{"alpha", "bravo", "charlie"}
	if len(keys) != len(want) {
		t.Fatalf("Keys len = %d, want %d", len(keys), len(want))
	}
	for i := range want {
		if keys[i] != want[i] {
			t.Fatalf("Keys[%d] = %q, want %q", i, keys[i], want[i])
		}
	}
}

func TestPersistenceRoundTrip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "snapshot.json")

	s1, err := Open(path)
	if err != nil {
		t.Fatalf("Open returned error: %v", err)
	}
	s1.Set("persisted", "yes")
	s1.Set("count", "42")
	if err := s1.Flush(); err != nil {
		t.Fatalf("Flush returned error: %v", err)
	}

	s2, err := Open(path)
	if err != nil {
		t.Fatalf("reopen returned error: %v", err)
	}
	if got, _ := s2.Get("persisted"); got != "yes" {
		t.Fatalf("reloaded persisted = %q, want %q", got, "yes")
	}
	if got, _ := s2.Get("count"); got != "42" {
		t.Fatalf("reloaded count = %q, want %q", got, "42")
	}
	if s2.Len() != 2 {
		t.Fatalf("reloaded Len = %d, want 2", s2.Len())
	}
}

func TestConcurrentAccess(t *testing.T) {
	s := New()
	var wg sync.WaitGroup
	for i := 0; i < 100; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			key := "key"
			s.Set(key, "value")
			_, _ = s.Get(key)
			_ = s.Keys()
		}(i)
	}
	wg.Wait()
	if got, _ := s.Get("key"); got != "value" {
		t.Fatalf("after concurrent access Get = %q, want %q", got, "value")
	}
}
