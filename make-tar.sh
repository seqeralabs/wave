gtar --transform "s/^layer/\/foo/" -vcf foo.tar layer
gtar --transform "s/^layer/\/foo/" -vczf foo.tar.gzip layer