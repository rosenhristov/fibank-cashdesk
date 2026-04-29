# CASH DESK MODULE

## Description:
Develop a cash operations module to support deposits, withdrawals, and balance checks for multiple
cashiers in BGN and EUR currencies. Each cashier has a starting balance in both currencies. The
module should allow checking balances for specific date ranges, as well as filtering by cashier name.
    
## Business Rules & Requirements:
- Initialize 3 cashiers (MARTINA, PETER, LINDA), each with their own starting balance in BGN
  and EUR
- Store each cashier’s balances and transactions history in in-memory data structures or files
- Starting amount 1000 BGN, denominations: 50x10, 10x50
- Starting amount 2000 EUR, denominations: 100x10, 20x50
- Withdrawal: 100 BGN, denominations: 5x10 BGN, 1x50 BGN
- Withdrawal: 500 EUR, denominations: 10x50 EUR
- Deposit: 600 BGN, denominations: 10x10 BGN, 10x50 BGN
- Deposit: 200 EUR, denominations: 5x20 EUR, 2x50 EUR
  
## Technical Requirements:
- Use GitHub repository with a README.md
- Use Java 17
- Use Maven
- Use Spring Boot
- Use validation for the API requests
- Use JSON format for the requests and responses
- Create controllers for cash operations as well as balance check:
  - `/api/v1/cash-operation` endpoint: Deposits and withdrawals must be part of one and the same API
    method
  -  `/api/v1/cash-balance` endpoint: Balance and denominations are returned from one and the same API
    method. It should contain dateFrom, dateTo and cashier parameters which are optional.
- Use a custom request header named `FIB-X-AUTH` with API key = `f9Uie8nNf112hx8s` for all API
  calls. Store it in suitable place and suitable format withing the project.
- Use Slf4J to log each action
- Create a Postman collection and environment. Add them within the git project.
- Use separate TXT file for transaction history. Find the most simple, fast and reliable way to format
  and structure the file.
- Use separate TXT file for cash balances and denominations. Find the most simple, fast and reliable
  way to format and structure the file.
  
## Output:
- Answer Deadline: 96 hours after receiving the email with the assignment
- Answer Format: Link to GitHub repository
- Answer Recipient: Send the answer formatted as stated above before the deadline to the following
  recipients: daniel.baykov@fibank.bg and bilyana.a.ivanova@fibank.bg
- After cloning the project from the repository, building it, running it locally and executing the
  authenticated Postman requests, the output of the cash-balance request must have the right balances and
  denominations for BGN and EUR for each cashier.
- Clarifications will not be provided. Conduct the development based solely on this document.