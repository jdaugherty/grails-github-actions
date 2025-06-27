<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Grails post-release action

Performs some actions after doing a release

## Requirements

1. Github Actions must be allowed to create pull requests in the repository. You can configure this in the repository settings under "Actions" -> "General" -> "Workflow permissions".
2. The Github Workflow step must have the `pull-requests: write` permission.

## Example usage

```yaml
uses: apache/grails-github-actions/post-release@asf
with:
  token: ${{ secrets.GITHUB_TOKEN }}
```
