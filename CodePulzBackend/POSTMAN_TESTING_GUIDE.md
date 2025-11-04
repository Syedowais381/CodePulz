# Postman Testing Guide for Interactive Code Execution API

## Prerequisites
1. Make sure your Spring Boot application is running (typically on `http://localhost:8080`)
2. Have Postman installed

## API Endpoints Overview

### Base URL
```
http://localhost:8080/api/v1
```

---

## Test 1: Start Interactive Execution (Java Example with Scanner)

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/v1/execute/start`
- **Headers:**
  ```
  Content-Type: application/json
  ```
- **Body (raw JSON):**
  ```json
  {
    "language": "java",
    "code": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World!\");\n        System.out.print(\"Enter a number: \");\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        System.out.println(\"You entered: \" + n);\n        System.out.println(\"Result: \" + (n * 2));\n    }\n}"
  }
  ```

### Expected Response
```json
{
  "output": "Hello World!\nEnter a number: ",
  "error": "",
  "executionTimeMs": 300,
  "sessionId": "abc-123-xyz-...",
  "isWaitingForInput": true,
  "isComplete": false
}
```

**Important:** Copy the `sessionId` from this response! You'll need it for the next step.

---

## Test 2: Send Input to Running Session

### Request
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/v1/execute/{sessionId}/input`
  
  Replace `{sessionId}` with the sessionId you got from Test 1.
  
  Example: `http://localhost:8080/api/v1/execute/abc-123-xyz-.../input`

- **Headers:**
  ```
  Content-Type: application/json
  ```
- **Body (raw JSON):**
  ```json
  {
    "sessionId": "abc-123-xyz-...",
    "input": "42"
  }
  ```
  
  **Important:** Replace `"abc-123-xyz-..."` with the actual sessionId from Test 1!

### Expected Response
```json
{
  "output": "Hello World!\nEnter a number: \nYou entered: 42\nResult: 84",
  "error": "",
  "executionTimeMs": 800,
  "sessionId": "abc-123-xyz-...",
  "isWaitingForInput": false,
  "isComplete": true
}
```

---

## Test 3: Check Session Status (Optional)

### Request
- **Method:** `GET`
- **URL:** `http://localhost:8080/api/v1/execute/{sessionId}/status`
  
  Replace `{sessionId}` with your sessionId.

### Expected Response
```json
{
  "output": "Hello World!\nEnter a number: \nYou entered: 42\nResult: 84",
  "error": "",
  "executionTimeMs": 850,
  "sessionId": "abc-123-xyz-...",
  "isWaitingForInput": false,
  "isComplete": true
}
```

---

## Test 4: Python Example with input()

### Step 1: Start Execution
**POST** `http://localhost:8080/api/v1/execute/start`

**Body:**
```json
{
  "language": "python",
  "code": "print('Hello World!')\nname = input('Enter your name: ')\nprint(f'Hello, {name}!')\nage = input('Enter your age: ')\nprint(f'You are {age} years old.')"
}
```

**Response:** Copy the `sessionId`

### Step 2: Send First Input
**POST** `http://localhost:8080/api/v1/execute/{sessionId}/input`

**Body:**
```json
{
  "sessionId": "your-session-id-here",
  "input": "John"
}
```

**Response:** Will show output with "Hello, John!" and will indicate it's waiting for more input.

### Step 3: Send Second Input
**POST** `http://localhost:8080/api/v1/execute/{sessionId}/input`

**Body:**
```json
{
  "sessionId": "your-session-id-here",
  "input": "25"
}
```

**Response:** Will show complete output with both inputs processed.

---

## Test 5: Multiple Inputs (Java)

### Step 1: Start Execution
**POST** `http://localhost:8080/api/v1/execute/start`

**Body:**
```json
{
  "language": "java",
  "code": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        System.out.print(\"Enter first number: \");\n        int a = sc.nextInt();\n        System.out.print(\"Enter second number: \");\n        int b = sc.nextInt();\n        System.out.println(\"Sum: \" + (a + b));\n    }\n}"
}
```

**Response:** Copy `sessionId`, will show "Enter first number: "

### Step 2: Send First Input
**POST** `http://localhost:8080/api/v1/execute/{sessionId}/input`

**Body:**
```json
{
  "sessionId": "your-session-id",
  "input": "10"
}
```

**Response:** Will show "Enter second number: " and `isWaitingForInput: true`

### Step 3: Send Second Input
**POST** `http://localhost:8080/api/v1/execute/{sessionId}/input`

**Body:**
```json
{
  "sessionId": "your-session-id",
  "input": "20"
}
```

**Response:** Will show complete output with "Sum: 30" and `isComplete: true`

---

## Postman Collection Setup Tips

1. **Create a Collection:**
   - Click "New" → "Collection"
   - Name it "CodePulz Interactive API"

2. **Set Collection Variables:**
   - Click on your collection → "Variables" tab
   - Add variable: `base_url` = `http://localhost:8080/api/v1`
   - Add variable: `session_id` = (leave empty, will be set dynamically)

3. **Create Request Templates:**
   - Save each endpoint as a separate request in your collection
   - Use variables like `{{base_url}}` in your URLs
   - Use `{{session_id}}` in your request bodies

4. **Automate Session ID Extraction:**
   - In the "Start Execution" request, go to "Tests" tab
   - Add this script to save sessionId:
   ```javascript
   var jsonData = pm.response.json();
   pm.collectionVariables.set("session_id", jsonData.sessionId);
   console.log("Session ID saved:", jsonData.sessionId);
   ```

5. **Use Pre-request Scripts:**
   - In the "Send Input" request, add this to the "Pre-request Script" tab:
   ```javascript
   pm.request.body.raw = JSON.stringify({
       "sessionId": pm.collectionVariables.get("session_id"),
       "input": pm.collectionVariables.get("user_input") || "42"
   });
   ```

---

## Quick Test Checklist

- [ ] Application is running on port 8080
- [ ] Created POST request to `/api/v1/execute/start`
- [ ] Set Content-Type header to `application/json`
- [ ] Sent Java code with Scanner
- [ ] Received response with `sessionId` and initial output
- [ ] Created POST request to `/api/v1/execute/{sessionId}/input`
- [ ] Sent input value
- [ ] Received complete output with results

---

## Troubleshooting

1. **"Session not found or expired":**
   - Sessions expire after 5 minutes of inactivity
   - Start a new session with `/execute/start`

2. **No output in response:**
   - Wait a moment and check status with `/execute/{sessionId}/status`
   - The process might still be running

3. **Connection refused:**
   - Make sure Spring Boot application is running
   - Check if it's on port 8080 (or your configured port)

4. **Empty output initially:**
   - This is normal - some programs don't print immediately
   - Send input anyway, output will appear after input is processed

---

## Example: Complete Test Flow

1. **Start Execution:**
   ```
   POST http://localhost:8080/api/v1/execute/start
   Body: { "language": "java", "code": "..." }
   → Get sessionId: "xyz-789"
   ```

2. **Send Input:**
   ```
   POST http://localhost:8080/api/v1/execute/xyz-789/input
   Body: { "sessionId": "xyz-789", "input": "42" }
   → Get final output
   ```

3. **Check Status (if needed):**
   ```
   GET http://localhost:8080/api/v1/execute/xyz-789/status
   → Get current status and output
   ```

