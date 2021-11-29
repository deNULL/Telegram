package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class ReactionsController extends BaseController {

    private int reactionsHash = 0;
    private ArrayList<TLRPC.TL_availableReaction> reactions = new ArrayList<>();

    private Runnable lastRunnable = null;

    private static volatile ReactionsController[] Instance = new ReactionsController[UserConfig.MAX_ACCOUNT_COUNT];

    public static ReactionsController getInstance(int num) {
        ReactionsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (ReactionsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ReactionsController(num);
                }
            }
        }
        return localInstance;
    }

    public ReactionsController(int num) {
        super(num);
        reloadAvailableReactions();
    }

    public void reloadAvailableReactions() {
        if (lastRunnable != null) {
            Utilities.globalQueue.cancelRunnable(lastRunnable);
            lastRunnable = null;
        }

        TLRPC.TL_messages_getAvailableReactions req = new TLRPC.TL_messages_getAvailableReactions();
        req.hash = reactionsHash;

        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (lastRunnable != null) {
                Utilities.globalQueue.cancelRunnable(lastRunnable);
            }
            lastRunnable = () -> reloadAvailableReactions();

            // Update again in an hour
            Utilities.globalQueue.postRunnable(lastRunnable, 3600 * 1000);

            if (response instanceof TLRPC.TL_messages_availableReactionsNotModified) {
                return;
            }
            if (response instanceof TLRPC.TL_messages_availableReactions) {
                int hash = ((TLRPC.TL_messages_availableReactions) response).hash;
                if (reactionsHash == hash) {
                    return;
                }
                reactions = ((TLRPC.TL_messages_availableReactions) response).reactions;
            }
            getNotificationCenter().postNotificationName(NotificationCenter.availableReactionsChanged);
        }));
    }

    public TLRPC.TL_availableReaction getReaction(String emoji) {
        for (TLRPC.TL_availableReaction reaction : reactions) {
            if (reaction.reaction == emoji) {
                return reaction;
            }
        }
        reloadAvailableReactions();
        return null;
    }
}
