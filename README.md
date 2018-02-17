# gradle2nix

Given a Gradle file, this program will generate a Nix expression
that evaluates to a store path containing the Gradle repository.

# Build instructions

## Compile

```
$ make build
```

## Test

```
$ make test
```


## Run

```
mvn exec:java -Dexec.args="pom.xml"
```