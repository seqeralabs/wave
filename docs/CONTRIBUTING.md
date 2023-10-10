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

#### Admonitions

We use admonitions extensively to make certain pieces of content stand out.
Please see the [official docs](https://squidfunk.github.io/mkdocs-material/reference/admonitions/) for an explanation.

-   `!!!` does a regular admonition, `???` makes it collapsed (click to expand).
-   Intendation is important! Make sure you check the rendered site, as it's easy to make a mistake.

### Formatting / linting

The docs use Prettier (<https://prettier.io/>) to ensure consistent markdown formatting.
There are a couple of ways that you can use this.

#### Pre-commit

Pre-commit (<https://pre-commit.com>) is a tool to run fast tests locally, as part of `git commit`.
If anything fails, it will be automatically fixed (if possible) and the commit aborted.
You can then stage the updates (or fix manually if needed) and try again.

To install for this repo:

```bash
pip install pre-commit # install pre-commit tool globally
pre-commit install     # in root of wave repo, activate pre-commit
```

Then a typical workflow for a commit:

```bash
git commit -a          # Normal git workflow, will run tests before allowing commit to proceed.
# If pre-commit found something, it fixes it and >> aborts the commit <<
git add .              # Stage the new changes made by prettier / pre-commit
git commit             # Try the commit command again, should now run as normal
```

GitHub actions CI runs the same thing, so will fail if edits are made where pre-commit didn't run locally

> **Warning**
> If pre-commit finds something it will typically fix it for you, but it will abort the commit. So you'll then need to stage the new files and repeat the commit

#### VSCode plugin / CLI

You can also install Prettier locally and run it as a command line too:

```
prettier -w .
```

There's also a [VSCode plugin](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) to auto-format on save, which is by far the easiest.

This fixes formatting as you go along, and pre-commit never has to do anything

> **Warning**
> May need a little config tweaking in VSCode to make it only run when you want it to run. For example I recommend at least this setting so that it doesn't start running on all code everywhere, but only when it's been configured as done in this PR:
> <img width="707" alt="image" src="https://user-images.githubusercontent.com/465550/223406793-5b952b27-8292-4249-9d66-f157177df417.png">
