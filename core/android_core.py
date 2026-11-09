import json
import subprocess


class AndroidCore:

    def __init__(self):
        self.actions = {
            "get_battery": self.get_battery,
            "vibrate": self.vibrate,
            "show_notification": self.show_notification,
        }

    def run_command(self, command, timeout=10):
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=timeout,
                check=True
            )
            return {
                "success": True,
                "output": result.stdout.strip()
            }
        except Exception as error:
            return {
                "success": False,
                "error": str(error)
            }

    def get_battery(self):
        result = self.run_command(
            ["termux-battery-status"]
        )

        if result["success"]:
            try:
                result["output"] = json.loads(result["output"])
            except json.JSONDecodeError:
                result["success"] = False
                result["error"] = "Invalid battery response"

        return result

    def vibrate(self):
        return self.run_command(
            ["termux-vibrate", "-d", "150"]
        )

    def show_notification(self, title, message):
        return self.run_command([
            "termux-notification",
            "--title", str(title)[:100],
            "--content", str(message)[:500]
        ])

    def execute(self, action, **kwargs):
        if action not in self.actions:
            return {
                "success": False,
                "error": "Action not permitted"
            }

        try:
            return self.actions[action](**kwargs)
        except Exception as error:
            return {
                "success": False,
                "error": str(error)
            }
