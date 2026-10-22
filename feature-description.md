TODO: markitDONE
The application should use the newest Spring boot with java 25. For the frontend use Angular, use primeng for icons and components as the library.
This is a simple todo application.
A todo should have:
 - descriptoin
 - name
 - expiration date
 - priority
 - category
 - recurrance (after occurrances/on exact date, start date)
Multiple views for the summary of the todos to explore your upcoming tasks:
 - weekly (1 week on the screen, with a pagination showing which week it is e.g: oct 7-oct 14)
 - 2 days (today and tomorrow, with the same pagination as in the weekly)
The applicaiton should have authentication via accounts (registration, forget password, login, logout).
Recurring todos should have a sign to differentiate from them. They should also have a list where i can list only those.
Each todo should be editable at any point, even if its in the past, (even recurring ones, not affecting past ones).
Each todo like editing can be deleted. If you delete a recurring todo 2 things can happen:
 1. if you delete the actual occurrance then you delete only that wihtout canceling the recurrance of it.
 2. if you delete from the recurring list, then you cancel the recurrance and from that point that todo will no longer automatically renew.
Use a stored logging for each action to help trace bugs back.
