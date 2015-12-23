SHELL := bash

SOURCE_FILES = src/main/kotlin/Main.kt                 \
               src/test/kotlin/MainTest.kt             \
               src/main/kotlin/ReadPOM.kt              \
               src/main/kotlin/ResolveDependencies.kt  \
               src/main/kotlin/DependencyDumperBoot.kt \
               src/main/kotlin/FileWrapper.kt

MVN = mvn

MVN_FLAGS = -T 8

RUN_MVN = $(MVN) $(MVN_FLAGS)

TEST_ARGS = "$$PWD/pom.xml"

all: build

build: $(SOURCE_FILES)
	$(RUN_MVN) "compile"
	$(RUN_MVN) "test-compile"

test: build
	$(RUN_MVN) "exec:java" -Dexec.args=$(TEST_ARGS)
