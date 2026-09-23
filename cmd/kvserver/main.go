// Command kvserver runs the kv-zero HTTP key-value store.
package main

import (
	"context"
	"errors"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/yongzhiai/kv-zero/internal/httpapi"
	"github.com/yongzhiai/kv-zero/internal/store"
)

func main() {
	addr := flag.String("addr", envOr("KV_ADDR", ":8080"), "address to listen on")
	dataPath := flag.String("data", envOr("KV_DATA", "data/kv.json"), "path to the JSON snapshot file")
	flag.Parse()

	s, err := store.Open(*dataPath)
	if err != nil {
		log.Fatalf("failed to open store at %s: %v", *dataPath, err)
	}

	srv := &http.Server{
		Addr:              *addr,
		Handler:           httpapi.New(s).Handler(),
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		log.Printf("kv-zero listening on %s (data: %s)", *addr, *dataPath)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server error: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop

	log.Println("shutting down...")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("graceful shutdown failed: %v", err)
	}
	if err := s.Flush(); err != nil {
		log.Printf("final flush failed: %v", err)
	}
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
