# How should I adopt the file structure to my needs:

By initializing your repository the TOSCom has generated some useful documents. Some of them are mandatory and must not been changed, some of them are mandatory, but should be attuned to your needs, and some of them are optional. Here is a description of the documents, their purposes, and what you may/should (not) do with them:

* **4THEREPOWENER.md** :- this file
* **AUTHORS** :- It is good tradition in GPL licensed projects to give the contributors credit by naming them explicitly.
- _YOU SHOULD NOT DELETE THIS FILE!_
- Complete the list of contributors by your main contributors.
- Insert a name if the respective person has gained copyrights (has done more than only correcting typos)
* **CODE_OF_CONDUCT.md** :- some well known rules how contributors should behave:
- You may erase this file if you do not need it
- If you are going to use it, please replace the contact address ``opensource@telekom.de`` by your own address
* **CODEOWNERS** :- a list of persons denoted by their GitHub accounts who are shall handle with pull requests
- You may erase this file if you do not need it - but to have such a file (and the respective persons) is a good practice if you use GitHub
- Append your code owners identifiers to the initially inserted person @kreincke.
- Erase the initially inserted codeowner ``kreincke``
* **codestyle/checkstyle.xml** :- a description how to write source code
- You may erase or replace this file in accordance to your needs.
* **CONTRIBUTING.md** :- a description how the community can contribute to your project
- You may erase this file if you do not need it.
- If you delete it, find another method (CLA etc.) to enforce your contributors also to license their work under the terms of the same license you've selected for your project.
- If you are going to use it, please replace the contact address ``opensource@telekom.de`` by your own address
* The licens text of and for your project
- _YOU MAY NOT DELETE THIS FILE!_
* **OSDF.md** :- a template for creating the Open Source Declaration File: If you bundle and distribute open source 3rd party software together with the package compiled from your repo, then you also must distribute some compliance artifacts. One method to do so is to fill in / create such a declaration file. In opposite of the THIRD-PARTY-NOTICES this file must only contain the components you really distribute together with your software, it is not intended to be a complete dependency documentation.  
* **README.md** :- the general project description file
-  _YOU MAY NOT DELETE THIS FILE!_
- _YOU MAY NOT DELETE THE LICENSING STATEMENT AT THE END OF THE FILE!_
- You may and should adopt the content to your needs. The initially inserted structure is best practice.
* **SECURITY.md** :- a description to which address vulnerabilities should be made known
- You may erase this file if you do not need it.
- If you are going to use it, please replace the contact address ``opensource@telekom.de`` by your own address
* **THIRD-PARTY-NOTICES** :- a list of dependencies which are required by the project / product.
* **templates/fileheader.txt** :- a template for initializing any new files
- Each file must contain an instantiated version of the fileheader.txt
- You must adopt the copyright line to your reality
