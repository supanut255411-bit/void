package dev.voidreaver.client;

/** Client copy of the cooldown state the server sent, counted down locally for the HUD bars. The server stays the authority. */
final class ClientCooldowns {
	static int chainLeft;
	static int chainTotal = 80;
	static int ultLeft;
	static int ultTotal = 1200;

	private ClientCooldowns() { }

	static void set(int chainLeft, int chainTotal, int ultLeft, int ultTotal) {
		ClientCooldowns.chainLeft = chainLeft;
		ClientCooldowns.chainTotal = Math.max(1, chainTotal);
		ClientCooldowns.ultLeft = ultLeft;
		ClientCooldowns.ultTotal = Math.max(1, ultTotal);
	}

	static void tick() {
		if (chainLeft > 0) {
			chainLeft--;
		}

		if (ultLeft > 0) {
			ultLeft--;
		}
	}

	static void clear() {
		chainLeft = 0;
		ultLeft = 0;
	}
}
