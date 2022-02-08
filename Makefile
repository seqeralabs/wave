all: layer.json

clean:
	rm layer*

.layer/opt/goofys/goofys:
	wget -O .layer/opt/goofys/goofys https://github.com/kahing/goofys/releases/download/v0.24.0/goofys
	chmod +x .layer/opt/goofys/goofys

layer.json: .layer/opt/goofys/goofys
	./make-tar.sh
