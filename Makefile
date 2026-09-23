.PHONY: build test vet run tidy clean

build:
	go build ./...

test:
	go test ./...

vet:
	go vet ./...

run:
	go run ./cmd/kvserver

tidy:
	go mod tidy

clean:
	rm -rf bin data
