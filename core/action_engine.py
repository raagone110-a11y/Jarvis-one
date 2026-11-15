from core.android_core import AndroidCore


class ActionEngine:

    CONFIRMATION_REQUIRED = {
        "vibrate",
        "show_notification"
    }

    ALLOWED_ACTIONS = {
        "get_battery",
        "vibrate",
        "show_notification"
    }

    def __init__(self):
        self.android = AndroidCore()

    def plan(self, user_text):
        if not isinstance(user_text, str):
            return None

        text = user_text.lower().strip()

        if any(word in text for word in [
            "battery status",
            "battery level",
            "battery kitni",
            "battery check",
            "phone battery"
        ]):
            return {
                "action": "get_battery",
                "kwargs": {}
            }

        if any(word in text for word in [
            "vibrate phone",
            "phone vibrate",
            "vibrate my phone"
        ]):
            return {
                "action": "vibrate",
                "kwargs": {}
            }

        if text.startswith("notify:"):
            content = user_text[7:].split("|", 1)

            if len(content) != 2:
                return {"error": "Format: notify: Title | Message"}

            title = content[0].strip()
            message = content[1].strip()

            if not title or not message:
                return {"error": "Title and message cannot be empty"}

            if len(title) > 100 or len(message) > 500:
                return {"error": "Notification text is too long"}

            return {
                "action": "show_notification",
                "kwargs": {
                    "title": title,
                    "message": message
                }
            }

        return None

    def execute(self, plan, approved=False):

        if not isinstance(plan, dict):
            return {"success": False, "error": "Invalid action plan"}

        action = plan.get("action")

        if action not in self.ALLOWED_ACTIONS:
            return {"success": False, "error": "Action not permitted"}

        # Approval policy is controlled here, never by the plan.
        if action in self.CONFIRMATION_REQUIRED and approved is not True:
            return {
                "success": False,
                "requires_confirmation": True,
                "message": "Owner permission required"
            }

        kwargs = plan.get("kwargs", {})

        if not isinstance(kwargs, dict):
            return {"success": False, "error": "Invalid action arguments"}

        if action in ("get_battery", "vibrate") and kwargs:
            return {"success": False, "error": "Unexpected action arguments"}

        if action == "show_notification":
            if set(kwargs.keys()) != {"title", "message"}:
                return {"success": False, "error": "Invalid notification arguments"}

            title = kwargs["title"]
            message = kwargs["message"]

            if not isinstance(title, str) or not isinstance(message, str):
                return {"success": False, "error": "Notification text must be strings"}

            if not title.strip() or not message.strip():
                return {"success": False, "error": "Notification text cannot be empty"}

            if len(title) > 100 or len(message) > 500:
                return {"success": False, "error": "Notification text is too long"}

        return self.android.execute(action, **kwargs)
