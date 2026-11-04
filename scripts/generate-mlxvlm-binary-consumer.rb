#!/usr/bin/env ruby
# Keep the repository's Ruby entry point; validation/generation shares Python tooling.
root = File.expand_path('..', __dir__)
abort('Usage: generate-mlxvlm-binary-consumer.rb ARTIFACT_ROOT MANIFEST OUTPUT') unless ARGV.length == 3
paths = ARGV.map { |path| File.expand_path(path) }
Dir.chdir(root) { exec('python3', '-m', 'tools.generate_mlxvlm_binary_consumer', *paths) }
