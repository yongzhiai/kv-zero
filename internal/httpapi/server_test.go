package httpapi

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/yongzhiai/kv-zero/internal/store"
)

func newTestServer() http.Handler {
	return New(store.New()).Handler()
}

func TestHealth(t *testing.T) {
	srv := newTestServer()
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("health status = %d, want %d", rec.Code, http.StatusOK)
	}
	if !strings.Contains(rec.Body.String(), `"status":"ok"`) {
		t.Fatalf("health body = %q, missing status ok", rec.Body.String())
	}
}

func TestPutGetDeleteFlow(t *testing.T) {
	srv := newTestServer()

	put := httptest.NewRequest(http.MethodPut, "/kv/greeting", strings.NewReader("hello"))
	putRec := httptest.NewRecorder()
	srv.ServeHTTP(putRec, put)
	if putRec.Code != http.StatusOK {
		t.Fatalf("PUT status = %d, want %d", putRec.Code, http.StatusOK)
	}

	get := httptest.NewRequest(http.MethodGet, "/kv/greeting", nil)
	getRec := httptest.NewRecorder()
	srv.ServeHTTP(getRec, get)
	if getRec.Code != http.StatusOK {
		t.Fatalf("GET status = %d, want %d", getRec.Code, http.StatusOK)
	}
	if !strings.Contains(getRec.Body.String(), `"value":"hello"`) {
		t.Fatalf("GET body = %q, missing value", getRec.Body.String())
	}

	del := httptest.NewRequest(http.MethodDelete, "/kv/greeting", nil)
	delRec := httptest.NewRecorder()
	srv.ServeHTTP(delRec, del)
	if delRec.Code != http.StatusOK {
		t.Fatalf("DELETE status = %d, want %d", delRec.Code, http.StatusOK)
	}

	getAfter := httptest.NewRequest(http.MethodGet, "/kv/greeting", nil)
	getAfterRec := httptest.NewRecorder()
	srv.ServeHTTP(getAfterRec, getAfter)
	if getAfterRec.Code != http.StatusNotFound {
		t.Fatalf("GET after delete status = %d, want %d", getAfterRec.Code, http.StatusNotFound)
	}
}

func TestGetMissingReturns404(t *testing.T) {
	srv := newTestServer()
	req := httptest.NewRequest(http.MethodGet, "/kv/nope", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("GET missing status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}

func TestListKeys(t *testing.T) {
	srv := newTestServer()
	for _, k := range []string{"a", "b"} {
		req := httptest.NewRequest(http.MethodPut, "/kv/"+k, strings.NewReader("v"))
		srv.ServeHTTP(httptest.NewRecorder(), req)
	}

	req := httptest.NewRequest(http.MethodGet, "/kv", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	body, _ := io.ReadAll(rec.Result().Body)
	if !strings.Contains(string(body), `"a"`) || !strings.Contains(string(body), `"b"`) {
		t.Fatalf("list body = %q, missing keys", string(body))
	}
}

func TestMethodNotAllowed(t *testing.T) {
	srv := newTestServer()
	req := httptest.NewRequest(http.MethodPost, "/kv/x", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("POST status = %d, want %d", rec.Code, http.StatusMethodNotAllowed)
	}
}
