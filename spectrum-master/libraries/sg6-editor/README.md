# G6-Editor

We no longer provide external support.
The original document is no longer available.
https://github.com/antvis/g6-editor/files/3101934/G6-Editor.pdf

### 说明

The G6-Editor project has been online for more than a year. Our initial goal was to showcase what G6 can do and ultimately provide a plug-and-play solution for everyone, and then open-source it once it gained traction in the industry. We added support for four templates in the demo, which many users used directly in their projects. However, we encountered some difficult-to-solve problems during the entire process:

- The scene of the graph editor is too complex, and the requirements of each business differ significantly, making it challenging to cover a domain with a single template.
- The plug-and-play approach reduces the cost of user access but shields the knowledge of the underlying G6, making the development process a steep learning curve.
- The details of interaction vary significantly in the business, with some interactions closely related to the business scenario, requiring expansion or modification.
- The cost of answering questions is huge, and the developers of the editor cannot sustain it.

Our solution is to refactor G6 and ensure that every function that each user needs is supported at the G6 bottom through mechanisms:

- Custom interaction
- Custom nodes
- Custom layout
- Command mode
- Communication between panels

We will also provide a simple editor's ideas and process. Thank you for your use and feedback, and we sincerely request your understanding and support.
