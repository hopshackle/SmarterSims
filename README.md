# SmarterSims
General documentation on functionality is available [here](https://docs.google.com/document/d/1zVXV5NJ-CwM_Y8R8tNk3xYZrZa9kqxAG2ndL4YRpooE/edit?usp=sharing).

This project should work out of the box if imported into IntelliJ. Specific points to note:

1) It uses Kotlin 1.3, and IntelliJ 2019.x or greater is needed.

2) It will also not compile without Junit libraries installed. 
These are provided with the default IntelliJ installation but not automatically included in the project path. 
The quickest way to include them is to go to one of the tests that fails to compile, place the cursor in one of the unresolved references and Alt-Enter...the top option in the drop-down list should then be to add the junit library to the path.
See...https://www.jetbrains.com/help/idea/configuring-testing-libraries.html
