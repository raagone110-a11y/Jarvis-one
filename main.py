import datetime

def jarvis():
    print("\n================================")
    print("       JARVIS ONE")
    print("================================")
    print("Hello! I am your personal assistant.")
    print("Type 'exit' to stop.\n")

    while True:
        command = input("You: ").lower().strip()

        if command in ["exit", "quit", "stop"]:
            print("Jarvis: Goodbye!")
            break

        elif "hello" in command or "hi" in command:
            print("Jarvis: Hello! How can I help you?")

        elif "time" in command or "samay" in command:
            now = datetime.datetime.now()
            print("Jarvis: Current time is", now.strftime("%I:%M %p"))

        elif "your name" in command or "naam" in command:
            print("Jarvis: My name is Jarvis One.")

        elif "help" in command:
            print("Jarvis: I am ready to assist you.")

        else:
            print("Jarvis: I am still learning this command.")

if __name__ == "__main__":
    jarvis()

