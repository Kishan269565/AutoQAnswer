package com.example.autoqanswer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AnswerProvider {
    
    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            generateTechnicalAnswer(question)
        } catch (e: Exception) {
            "I apologize, but I'm having trouble processing your question right now. Please try again."
        }
    }
    
    private fun generateTechnicalAnswer(question: String): String {
        val lowerQuestion = question.toLowerCase(Locale.ROOT)
        
        return when {
            // Programming Languages
            lowerQuestion.contains("what is python") -> 
                "Python is a high-level, interpreted programming language known for its simplicity and readability. It's widely used for web development, data analysis, AI, and scientific computing."
            
            lowerQuestion.contains("what is java") -> 
                "Java is an object-oriented programming language designed to have minimal implementation dependencies. It follows 'write once, run anywhere' principle using JVM."
            
            lowerQuestion.contains("what is javascript") -> 
                "JavaScript is a scripting language primarily used for web development to create interactive effects within web browsers. It's essential for front-end development."
            
            // Programming Concepts
            lowerQuestion.contains("how to create a function") -> 
                "To create a function:\nPython: def function_name(params):\nJava: public void functionName(params) {}\nJavaScript: function functionName(params) {}"
            
            lowerQuestion.contains("difference between list and array") -> 
                "List: Dynamic size, flexible operations\nArray: Fixed size, better performance\nIn Python, lists are dynamic while arrays need import from array module."
            
            lowerQuestion.contains("what is oop") || lowerQuestion.contains("object oriented programming") -> 
                "OOP principles:\n- Encapsulation: Bundling data with methods\n- Inheritance: Creating new classes from existing ones\n- Polymorphism: Same interface, different implementations\n- Abstraction: Hiding complex reality while exposing essentials"
            
            // Web Development
            lowerQuestion.contains("what is html") -> 
                "HTML (HyperText Markup Language) is the standard markup language for documents designed to be displayed in a web browser. It structures web content."
            
            lowerQuestion.contains("what is css") -> 
                "CSS (Cascading Style Sheets) is used to describe the presentation of HTML documents, including colors, layout, and fonts. It separates content from design."
            
            lowerQuestion.contains("what is react") -> 
                "React is a JavaScript library for building user interfaces, particularly web applications. It uses component-based architecture and virtual DOM for efficient updates."
            
            // Data Structures & Algorithms
            lowerQuestion.contains("what is linked list") -> 
                "A linked list is a linear data structure where elements are stored in nodes, each containing a data field and reference to the next node. Types: singly, doubly, circular."
            
            lowerQuestion.contains("bubble sort") -> 
                "Bubble Sort repeatedly steps through the list, compares adjacent elements and swaps them if they are in wrong order. Time complexity: O(n²)"
            
            lowerQuestion.contains("binary search") -> 
                "Binary Search finds the position of a target value within a sorted array by repeatedly dividing the search interval in half. Time complexity: O(log n)"
            
            // Database
            lowerQuestion.contains("what is sql") -> 
                "SQL (Structured Query Language) is used to communicate with databases. Common commands: SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP."
            
            lowerQuestion.contains("difference between sql and nosql") -> 
                "SQL: Structured schema, ACID properties, vertical scaling\nNoSQL: Flexible schema, BASE properties, horizontal scaling\nSQL: MySQL, PostgreSQL | NoSQL: MongoDB, Cassandra"
            
            // Tools
            lowerQuestion.contains("what is git") -> 
                "Git is a distributed version control system. Key commands: git clone, git add, git commit, git push, git pull, git branch, git merge."
            
            lowerQuestion.contains("what is docker") -> 
                "Docker is a platform for developing, shipping, and running applications in containers. Containers package an application with all its dependencies."
            
            // General Technical
            lowerQuestion.contains("what is api") -> 
                "API (Application Programming Interface) is a set of rules that allows programs to talk to each other. REST APIs use HTTP methods: GET, POST, PUT, DELETE."
            
            // Coding Questions
            lowerQuestion.contains("reverse a string") -> 
                "Python: text[::-1]\nJava: new StringBuilder(text).reverse().toString()\nJavaScript: text.split('').reverse().join('')"
            
            lowerQuestion.contains("fibonacci sequence") -> 
                "Fibonacci: Each number is sum of two preceding ones.\nPython:\ndef fib(n):\n    a, b = 0, 1\n    for _ in range(n):\n        print(a)\n        a, b = b, a+b"
            
            lowerQuestion.contains("palindrome check") -> 
                "Palindrome: Reads same forwards and backwards.\nPython: text == text[::-1]\nJava: text.equals(new StringBuilder(text).reverse().toString())"
            
            // Default answer
            else -> 
                "This appears to be a technical question. For detailed information, I recommend checking official documentation or specific tutorials on this topic."
        }
    }
}
