"""
Base class for all JARVIS agents.
Every specialized agent (Script, Research, Strategy, etc.) inherits from this.
"""

from abc import ABC, abstractmethod
from typing import Any, Dict, Optional
import datetime


class BaseAgent(ABC):
    """Common interface for all JARVIS agents."""

    name: str = "base"
    description: str = "Base agent"

    def __init__(self, gemini_key: Optional[str] = None):
        self.gemini_key = gemini_key
        self.created_at = datetime.datetime.now().isoformat()

    @abstractmethod
    def run(self, task: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Execute the agent task.

        Returns a standard result dict:
        {
            "success": bool,
            "agent": str,
            "output": Any,
            "error": Optional[str],
            "meta": dict
        }
        """
        pass

    def ok(self, output: Any, **meta) -> Dict[str, Any]:
        return {
            "success": True,
            "agent": self.name,
            "output": output,
            "error": None,
            "meta": meta or {}
        }

    def fail(self, error: str, **meta) -> Dict[str, Any]:
        return {
            "success": False,
            "agent": self.name,
            "output": None,
            "error": error,
            "meta": meta or {}
        }
