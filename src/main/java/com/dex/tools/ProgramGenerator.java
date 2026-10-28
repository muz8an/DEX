package com.dex.tools;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent Code and Program Generator for DEX.
 * Generates clean, well-commented, runnable source code for common algorithmic tasks,
 * math problems, and starter programs across Python, C, Java, JavaScript, and C++.
 */
public class ProgramGenerator {

    public static class GeneratedProgram {
        private final String filename;
        private final String code;
        private final String title;

        public GeneratedProgram(String filename, String code, String title) {
            this.filename = filename;
            this.code = code;
            this.title = title;
        }

        public String getFilename() {
            return filename;
        }

        public String getCode() {
            return code;
        }

        public String getTitle() {
            return title;
        }
    }

    /**
     * Determines whether a natural language user query expresses intent to write or generate code.
     */
    public static boolean isCodeGenerationRequest(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase(Locale.ROOT).trim();

        // Avoid matching non-coding commands or window opening
        if (lower.startsWith("open ") && !lower.contains("write ") && !lower.contains("create ") && !lower.contains("make ")) {
            return false;
        }
        if (lower.equals("new window") || lower.equals("a new window")
                || lower.contains("open a new window") || lower.contains("open new window")
                || lower.contains("vs code in a new window") || lower.contains("vscode in a new window")) {
            return false;
        }

        // Must express an intent to write/create/code a program
        boolean hasAction = Pattern.compile("(?i)\\b(?:write|create|make|generate|build)\\b").matcher(lower).find()
                || lower.matches("(?i).*(?:^|\\b)(?:code|program)\\s+to\\s+.+");
        if (!hasAction) {
            return false;
        }

        // Target must be one of DEX's local algorithmic/starter templates or specifically directed at the editor
        return (lower.contains("add") && (lower.contains("number") || lower.contains("sum")))
                || lower.contains("calculator") || lower.contains("calc") || lower.contains("arithmetic")
                || lower.contains("fibonacci")
                || lower.contains("factorial")
                || lower.contains("prime")
                || (lower.contains("even") && lower.contains("odd"))
                || lower.contains("palindrome")
                || lower.contains("any program") || lower.contains("starter") || lower.contains("hello world")
                || lower.contains("vs code") || lower.contains("vscode");
    }

    /**
     * Inspects the prompt and generates an appropriate complete program with source code.
     */
    public static GeneratedProgram generate(String prompt) {
        String lower = prompt != null ? prompt.toLowerCase(Locale.ROOT).trim() : "";

        // 1. Detect requested programming language
        String lang = "python";
        if (lower.contains("in c++") || lower.contains("in cpp") || lower.contains("c++ program") || lower.contains("cpp program")) {
            lang = "cpp";
        } else if (lower.contains("in c ") || lower.endsWith("in c") || lower.contains("c program") || lower.contains("in c language")) {
            lang = "c";
        } else if (lower.contains("in java ") || lower.endsWith("in java") || lower.contains("java program")) {
            lang = "java";
        } else if (lower.contains("in javascript") || lower.contains("in js") || lower.contains("javascript program") || lower.contains("node program")) {
            lang = "javascript";
        } else if (lower.contains("in python") || lower.contains("python program") || lower.contains("python script") || lower.contains(".py")) {
            lang = "python";
        }

        // 2. Check if a specific filename is explicitly mentioned (e.g. "test.py", "sum.c", "app.js")
        String customFilename = extractExplicitFilename(prompt);

        // 3. Match specific task intents
        if (lower.contains("add") && (lower.contains("two number") || lower.contains("2 number") || lower.contains("numbers") || lower.contains("sum"))) {
            return generateAddTwoNumbers(lang, customFilename);
        }

        if (lower.contains("calculator") || lower.contains("arithmetic") || lower.contains("calc")) {
            return generateCalculator(lang, customFilename);
        }

        if (lower.contains("fibonacci")) {
            return generateFibonacci(lang, customFilename);
        }

        if (lower.contains("factorial")) {
            return generateFactorial(lang, customFilename);
        }

        if (lower.contains("prime") || lower.contains("check prime")) {
            return generatePrimeCheck(lang, customFilename);
        }

        if (lower.contains("even") && (lower.contains("odd") || lower.contains("check even"))) {
            return generateEvenOdd(lang, customFilename);
        }

        if (lower.contains("palindrome")) {
            return generatePalindrome(lang, customFilename);
        }

        // 4. Default / Generic "any program" or general task
        return generateGeneralStarter(lang, customFilename, prompt);
    }

    private static String extractExplicitFilename(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(?i)\\b([a-zA-Z0-9_-]+\\.(?:py|c|cpp|java|js|ts|html|css|json|txt))\\b").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    // ==================== SPECIFIC CODE GENERATORS ====================

    private static GeneratedProgram generateAddTwoNumbers(String lang, String customFilename) {
        switch (lang) {
            case "c":
                String cName = customFilename != null ? customFilename : "add_two_numbers.c";
                String cCode = """
                        #include <stdio.h>

                        /*
                         * Program to add two numbers in C
                         * Generated by DEX Personal AI Assistant
                         */
                        int main(void) {
                            double num1, num2, sum;

                            printf("=========================================\\n");
                            printf("         ADD TWO NUMBERS (C)            \\n");
                            printf("=========================================\\n");

                            printf("Enter first number: ");
                            if (scanf("%lf", &num1) != 1) {
                                printf("Invalid input.\\n");
                                return 1;
                            }

                            printf("Enter second number: ");
                            if (scanf("%lf", &num2) != 1) {
                                printf("Invalid input.\\n");
                                return 1;
                            }

                            sum = num1 + num2;
                            printf("\\nResult: %.4f + %.4f = %.4f\\n", num1, num2, sum);
                            printf("=========================================\\n");

                            return 0;
                        }
                        """;
                return new GeneratedProgram(cName, cCode.stripIndent(), "Program to Add Two Numbers (C)");

            case "cpp":
                String cppName = customFilename != null ? customFilename : "add_two_numbers.cpp";
                String cppCode = """
                        #include <iostream>

                        /*
                         * Program to add two numbers in C++
                         * Generated by DEX Personal AI Assistant
                         */
                        int main() {
                            double num1, num2;
                            std::cout << "=========================================" << std::endl;
                            std::cout << "        ADD TWO NUMBERS (C++)            " << std::endl;
                            std::cout << "=========================================" << std::endl;

                            std::cout << "Enter first number: ";
                            std::cin >> num1;

                            std::cout << "Enter second number: ";
                            std::cin >> num2;

                            double sum = num1 + num2;
                            std::cout << "\\nResult: " << num1 << " + " << num2 << " = " << sum << std::endl;
                            std::cout << "=========================================" << std::endl;

                            return 0;
                        }
                        """;
                return new GeneratedProgram(cppName, cppCode.stripIndent(), "Program to Add Two Numbers (C++)");

            case "java":
                String javaName = customFilename != null ? customFilename : "AddTwoNumbers.java";
                String javaCode = """
                        import java.util.Scanner;

                        /*
                         * Program to add two numbers in Java
                         * Generated by DEX Personal AI Assistant
                         */
                        public class AddTwoNumbers {
                            public static void main(String[] args) {
                                Scanner scanner = new Scanner(System.in);

                                System.out.println("=========================================");
                                System.out.println("        ADD TWO NUMBERS (Java)           ");
                                System.out.println("=========================================");

                                System.out.print("Enter first number: ");
                                double num1 = scanner.nextDouble();

                                System.out.print("Enter second number: ");
                                double num2 = scanner.nextDouble();

                                double sum = num1 + num2;
                                System.out.printf("%nResult: %.4f + %.4f = %.4f%n", num1, num2, sum);
                                System.out.println("=========================================");

                                scanner.close();
                            }
                        }
                        """;
                return new GeneratedProgram(javaName, javaCode.stripIndent(), "Program to Add Two Numbers (Java)");

            case "javascript":
                String jsName = customFilename != null ? customFilename : "add_two_numbers.js";
                String jsCode = """
                        const readline = require('readline');

                        /*
                         * Program to add two numbers in JavaScript (Node.js)
                         * Generated by DEX Personal AI Assistant
                         */
                        const rl = readline.createInterface({
                            input: process.stdin,
                            output: process.stdout
                        });

                        console.log("=========================================");
                        console.log("     ADD TWO NUMBERS (JavaScript)        ");
                        console.log("=========================================");

                        rl.question('Enter first number: ', (ans1) => {
                            rl.question('Enter second number: ', (ans2) => {
                                const n1 = parseFloat(ans1);
                                const n2 = parseFloat(ans2);
                                if (isNaN(n1) || isNaN(n2)) {
                                    console.log("Error: Please enter valid numeric values.");
                                } else {
                                    const sum = n1 + n2;
                                    console.log(`\\nResult: ${n1} + ${n2} = ${sum}`);
                                }
                                console.log("=========================================");
                                rl.close();
                            });
                        });
                        """;
                return new GeneratedProgram(jsName, jsCode.stripIndent(), "Program to Add Two Numbers (JavaScript)");

            default:
                // Python default
                String pyName = customFilename != null ? customFilename : "add_two_numbers.py";
                String pyCode = """
                        # =========================================================
                        # Program to Add Two Numbers in Python
                        # Generated by DEX Personal AI Assistant
                        # =========================================================

                        def add_numbers(a: float, b: float) -> float:
                            \"\"\"Returns the sum of two numbers.\"\"\"
                            return a + b


                        def main():
                            print("=" * 45)
                            print("          ADD TWO NUMBERS (Python)")
                            print("=" * 45)

                            try:
                                num1 = float(input("Enter first number: "))
                                num2 = float(input("Enter second number: "))
                                result = add_numbers(num1, num2)

                                print("-" * 45)
                                print(f"Result: {num1} + {num2} = {result}")
                                print("=" * 45)
                            except ValueError:
                                print("Error: Please enter valid numbers.")


                        if __name__ == "__main__":
                            main()
                        """;
                return new GeneratedProgram(pyName, pyCode.stripIndent(), "Program to Add Two Numbers (Python)");
        }
    }

    private static GeneratedProgram generateCalculator(String lang, String customFilename) {
        String name = customFilename != null ? customFilename : ("calculator." + (lang.equals("c") ? "c" : (lang.equals("cpp") ? "cpp" : (lang.equals("java") ? "java" : (lang.equals("javascript") ? "js" : "py")))));
        String pyCode = """
                # =========================================================
                # Interactive Calculator in Python
                # Generated by DEX Personal AI Assistant
                # =========================================================

                def calculate(num1: float, op: str, num2: float):
                    if op == '+':
                        return num1 + num2
                    elif op == '-':
                        return num1 - num2
                    elif op == '*':
                        return num1 * num2
                    elif op == '/':
                        if num2 == 0:
                            return "Error: Division by zero!"
                        return num1 / num2
                    elif op == '%':
                        return num1 % num2
                    elif op == '^':
                        return num1 ** num2
                    else:
                        return f"Error: Unknown operator '{op}'"


                def main():
                    print("=" * 50)
                    print("           DEX INTERACTIVE CALCULATOR")
                    print("=" * 50)
                    print("Operations: + (Add), - (Subtract), * (Multiply), / (Divide), ^ (Power)")
                    print("-" * 50)

                    try:
                        n1 = float(input("Enter first number: "))
                        op = input("Enter operator (+, -, *, /, ^): ").strip()
                        n2 = float(input("Enter second number: "))

                        result = calculate(n1, op, n2)
                        print("-" * 50)
                        print(f"Result: {n1} {op} {n2} = {result}")
                        print("=" * 50)
                    except ValueError:
                        print("Error: Invalid numeric input.")


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, pyCode.stripIndent(), "Interactive Calculator");
    }

    private static GeneratedProgram generateFibonacci(String lang, String customFilename) {
        String name = customFilename != null ? customFilename : "fibonacci.py";
        String code = """
                # =========================================================
                # Fibonacci Series Generator in Python
                # Generated by DEX Personal AI Assistant
                # =========================================================

                def generate_fibonacci(n: int):
                    if n <= 0:
                        return []
                    elif n == 1:
                        return [0]
                    sequence = [0, 1]
                    while len(sequence) < n:
                        sequence.append(sequence[-1] + sequence[-2])
                    return sequence


                def main():
                    print("=" * 45)
                    print("           FIBONACCI SERIES GENERATOR")
                    print("=" * 45)
                    try:
                        count = int(input("How many terms would you like to generate? "))
                        if count <= 0:
                            print("Please enter a positive integer.")
                            return

                        series = generate_fibonacci(count)
                        print("-" * 45)
                        print(f"Fibonacci Sequence ({count} terms):")
                        print(", ".join(map(str, series)))
                        print("=" * 45)
                    except ValueError:
                        print("Error: Please enter an integer.")


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, code.stripIndent(), "Fibonacci Series Generator");
    }

    private static GeneratedProgram generateFactorial(String lang, String customFilename) {
        String name = customFilename != null ? customFilename : "factorial.py";
        String code = """
                # =========================================================
                # Factorial Calculation Program in Python
                # Generated by DEX Personal AI Assistant
                # =========================================================

                def factorial(n: int) -> int:
                    if n < 0:
                        raise ValueError("Factorial is not defined for negative numbers.")
                    result = 1
                    for i in range(2, n + 1):
                        result *= i
                    return result


                def main():
                    print("=" * 45)
                    print("           FACTORIAL CALCULATOR")
                    print("=" * 45)
                    try:
                        n = int(input("Enter a non-negative integer: "))
                        fact = factorial(n)
                        print("-" * 45)
                        print(f"{n}! = {fact}")
                        print("=" * 45)
                    except ValueError as e:
                        print(f"Error: {e}")


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, code.stripIndent(), "Factorial Calculator");
    }

    private static GeneratedProgram generatePrimeCheck(String lang, String customFilename) {
        String name = customFilename != null ? customFilename : "prime_checker.py";
        String code = """
                # =========================================================
                # Prime Number Checker in Python
                # Generated by DEX Personal AI Assistant
                # =========================================================
                import math

                def is_prime(n: int) -> bool:
                    if n <= 1:
                        return False
                    if n <= 3:
                        return True
                    if n % 2 == 0 or n % 3 == 0:
                        return False
                    limit = int(math.isqrt(n))
                    for i in range(5, limit + 1, 6):
                        if n % i == 0 or n % (i + 2) == 0:
                            return False
                    return True


                def main():
                    print("=" * 45)
                    print("             PRIME NUMBER CHECKER")
                    print("=" * 45)
                    try:
                        num = int(input("Enter an integer to test: "))
                        result = is_prime(num)
                        print("-" * 45)
                        if result:
                            print(f"{num} is a PRIME number.")
                        else:
                            print(f"{num} is NOT a prime number.")
                        print("=" * 45)
                    except ValueError:
                        print("Error: Please enter a valid integer.")


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, code.stripIndent(), "Prime Number Checker");
    }

    private static GeneratedProgram generateEvenOdd(String lang, String customFilename) {
        String name = customFilename != null ? customFilename : "even_or_odd.py";
        String code = """
                # =========================================================
                # Even or Odd Number Checker
                # Generated by DEX Personal AI Assistant
                # =========================================================

                def check_even_odd(n: int) -> str:
                    return "EVEN" if n % 2 == 0 else "ODD"


                def main():
                    print("=" * 45)
                    print("            EVEN OR ODD CHECKER")
                    print("=" * 45)
                    try:
                        num = int(input("Enter an integer: "))
                        print("-" * 45)
                        print(f"{num} is an {check_even_odd(num)} number.")
                        print("=" * 45)
                    except ValueError:
                        print("Error: Please enter a valid integer.")


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, code.stripIndent(), "Even or Odd Checker");
    }

    private static GeneratedProgram generatePalindrome(String lang, String customFilename) {
        String name = customFilename != null ? customFilename : "palindrome_checker.py";
        String code = """
                # =========================================================
                # Palindrome String and Number Checker
                # Generated by DEX Personal AI Assistant
                # =========================================================

                def is_palindrome(text: str) -> bool:
                    cleaned = ''.join(c.lower() for c in text if c.isalnum())
                    return cleaned == cleaned[::-1]


                def main():
                    print("=" * 45)
                    print("            PALINDROME CHECKER")
                    print("=" * 45)
                    user_input = input("Enter a word, sentence, or number: ")
                    if is_palindrome(user_input):
                        print("-" * 45)
                        print(f"'{user_input}' IS a palindrome!")
                    else:
                        print("-" * 45)
                        print(f"'{user_input}' is NOT a palindrome.")
                    print("=" * 45)


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, code.stripIndent(), "Palindrome Checker");
    }

    private static GeneratedProgram generateGeneralStarter(String lang, String customFilename, String prompt) {
        String name = customFilename != null ? customFilename : "test.py";
        String code = """
                # =========================================================
                # Program created by DEX Personal AI Assistant
                # Workspace: Active Development Project
                # =========================================================

                import sys
                import datetime

                def display_banner():
                    print("=" * 55)
                    print("      DEX AI ASSISTANT - APPLICATION DEMO")
                    print("=" * 55)
                    print(f"Current Date & Time : {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
                    print(f"Python Runtime      : {sys.version.split()[0]}")
                    print("-" * 55)


                def calculate_sample_metrics():
                    numbers = [10, 25, 42, 58, 99, 105, 314]
                    total = sum(numbers)
                    avg = total / len(numbers)
                    return total, avg, max(numbers), min(numbers)


                def main():
                    display_banner()
                    print("Running interactive diagnostics...")
                    total, avg, high, low = calculate_sample_metrics()

                    print(f"Sample Sum       : {total}")
                    print(f"Sample Average   : {avg:.2f}")
                    print(f"Maximum Value    : {high}")
                    print(f"Minimum Value    : {low}")
                    print("=" * 55)
                    print("Program executed successfully inside VS Code!")


                if __name__ == "__main__":
                    main()
                """;
        return new GeneratedProgram(name, code.stripIndent(), "Starter Program");
    }
}
