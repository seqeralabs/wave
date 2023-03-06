# Wave Documentation

## Contribution model

The typical workflow for contributing to the Wave documentation is as follows:

1. Make a _fork_ of the GitHub repository to your own account
2. Develop locally (see below) and make your changes
3. Commit and push to your forked repository
4. Open a pull-request against the main repo, which can be reviewed and merged

## Documentation

The Wave website is built using [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/).
It's a static site generator designed for documentation websites which is fast and lightweight and comes with a lot of nice features.
We use the open-source version of the tool (not any of the "insiders" features, currently).

To make changes, you should run the website locally so that you can preview changes.
All site content is held in the `docs` directory.

### Installation

See the [mkdocs material docs](https://squidfunk.github.io/mkdocs-material/getting-started/) for full installation instructions.
A short version for this site is below.

#### Docker

If you are used to using Docker and don't want to mess around with Python, you can run the following command to preview the site:

```bash
docker run --rm -it -p 8000:8000 -v ${PWD}:/docs squidfunk/mkdocs-material
```

This uses the mkdocs material [official docker image](https://hub.docker.com/r/squidfunk/mkdocs-material/).
You should get some output that looks like this:

```console
INFO     -  Documentation built in 27.56 seconds
INFO     -  [21:52:17] Watching paths for changes: 'docs', 'mkdocs.yml'
INFO     -  [21:52:17] Serving on http://0.0.0.0:8000/
```

Visit <http://0.0.0.0:8000/> in your web browser to view the site.
Pages will automatically refresh when you save changes in your editor.

#### Python

If you have a recent version of Python installed, then local installation should be as simple as:

```bash
pip install mkdocs-material
```

Once installed, you can view the site by running:

```bash
mkdocs serve
```

The log output will show a URL, probably <http://127.0.0.1:8000/> - open this in your browser to view the site.
Pages will automatically refresh when you save changes in your editor.

#### Social cards

The docs website auto-generates social sharing cards for every page. This sometimes causes issues when running locally, and needs a bunch of additional dependencies (already included in the docker image).
It's recommended to disable social card generation when testing locally. To do this, set the environment variable `CARDS` to `false`:

```bash
CARDS=false mkdocs serve
```

```bash
docker run --rm -it -p 8000:8000 -e 'CARDS=false' -v ${PWD}:/docs ghcr.io/nextflow-io/training-mkdocs:latest
```

### Figures & diagrams

Graphics should be drawn using [Excalidraw](https://excalidraw.com/).
Please use the [VSCode extension](https://marketplace.visualstudio.com/items?itemName=pomdtr.excalidraw-editor) and give files a `.excalidraw.svg` filename suffix.
Files will continue to be editable by others using this method.

Excalidraw SVGs should be embedded as follows:

<!-- prettier-ignore-start -->
```html
<figure class="excalidraw">
--8<-- "docs/basic_training/img/channel-files.excalidraw.svg"
</figure>
```
<!-- prettier-ignore-end -->

> Note: The file path is from the root of the repo, not the markdown file!

We inline the SVG into the content like this to make remotely loaded fonts work, as well as dark-mode compatibility.

### Content style and formatting

All training content must be written as markdown.

Please use triple-backslashes for code blocks with the language for syntax highlighting, _not_ indentation.
For example:

````markdown
```groovy
println "This has syntax highlighting!"
```
````

#### Formatting / linting

Please make sure that you have Prettier installed and working locally: <https://prettier.io/> (ideally via the VSCode plugin or similar, formatting on save).

There is a GitHub action that checks pull-requests for valid formatting.

#### Admonitions

We use admonitions extensively to make certain pieces of content stand out.
Please see the [official docs](https://squidfunk.github.io/mkdocs-material/reference/admonitions/) for an explanation.

-   `!!!` does a regular admonition, `???` makes it collapsed (click to expand).
-   Intendation is important! Make sure you check the rendered site, as it's easy to make a mistake.
