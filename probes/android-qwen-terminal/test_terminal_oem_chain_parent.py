"""Six-action chain with real ADB argument rejoining and TECNO absent exit1."""
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path[:0] = [str(HERE), str(HERE.parent / 'android-terminal-observation')]
import test_terminal_full_chain_parent as original_chain
from test_terminal_oem_shell_parent import real_shell


class OemFullChainParent(original_chain.TerminalFullChainParent):
    def setUp(self):
        super().setUp()
        real_shell(self.f)


if __name__ == '__main__':
    unittest.main(verbosity=2)
