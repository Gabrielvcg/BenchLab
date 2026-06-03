package main

import (
	"os"
	"testing"
)

func TestPrepareRunnerSpecRustUsesCompilerImage(t *testing.T) {
	tempDir := t.TempDir()

	spec, err := prepareRunnerSpec("RUST", "fn main() { println!(\"ok\"); }", tempDir)
	if err != nil {
		t.Fatalf("prepareRunnerSpec returned error: %v", err)
	}

	if spec.image != "rust:1-bookworm" {
		t.Fatalf("unexpected rust image: %s", spec.image)
	}

	if len(spec.compileCommand) == 0 {
		t.Fatal("expected rust compile command")
	}

	if len(spec.runCommand) != 1 || spec.runCommand[0] != "/workspace/benchlab-app" {
		t.Fatalf("unexpected rust run command: %#v", spec.runCommand)
	}

	if _, err := os.Stat(tempDir + string(os.PathSeparator) + "main.rs"); err != nil {
		t.Fatalf("expected rust source file to be created: %v", err)
	}
}
