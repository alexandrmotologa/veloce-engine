// VeloceEngine Interactive Web Dashboard Client

(function() {
    let currentSide = 'BUY';

    const btnBuy = document.getElementById('btn-buy');
    const btnSell = document.getElementById('btn-sell');
    const submitBtn = document.getElementById('submit-order-btn');
    const orderForm = document.getElementById('order-form');
    const ladderRows = document.getElementById('ladder-rows');
    const tapeList = document.getElementById('tape-list');

    const valSymbol = document.getElementById('val-symbol');
    const valBestBid = document.getElementById('val-best-bid');
    const valSpread = document.getElementById('val-spread');
    const valBestAsk = document.getElementById('val-best-ask');
    const valProcessed = document.getElementById('val-processed');

    const cancelOrderIdInput = document.getElementById('cancel-order-id');
    const btnCancel = document.getElementById('btn-cancel');

    // Side selection toggle
    btnBuy.addEventListener('click', () => {
        currentSide = 'BUY';
        btnBuy.classList.add('active');
        btnSell.classList.remove('active');
        submitBtn.textContent = 'Submit Buy Order';
        submitBtn.style.background = 'var(--bid-color)';
        submitBtn.style.color = '#000';
    });

    btnSell.addEventListener('click', () => {
        currentSide = 'SELL';
        btnSell.classList.add('active');
        btnBuy.classList.remove('active');
        submitBtn.textContent = 'Submit Sell Order';
        submitBtn.style.background = 'var(--ask-color)';
        submitBtn.style.color = '#fff';
    });

    // Order Submission
    orderForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const type = document.getElementById('order-type').value;
        const price = parseFloat(document.getElementById('order-price').value);
        const qty = parseInt(document.getElementById('order-qty').value, 10);

        if (isNaN(price) || isNaN(qty) || qty <= 0) {
            alert('Invalid order parameters');
            return;
        }

        const payload = {
            side: currentSide,
            type: type,
            price: price,
            qty: qty
        };

        try {
            const resp = await fetch('/api/order', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await resp.json();
            if (data.status === 'ACCEPTED') {
                submitBtn.textContent = `Accepted (#${data.orderId})`;
                setTimeout(() => {
                    submitBtn.textContent = currentSide === 'BUY' ? 'Submit Buy Order' : 'Submit Sell Order';
                }, 1200);
            }
        } catch (err) {
            console.error('Failed to submit order:', err);
        }
    });

    // Order Cancel
    btnCancel.addEventListener('click', async () => {
        const orderId = parseInt(cancelOrderIdInput.value, 10);
        if (isNaN(orderId) || orderId <= 0) {
            return;
        }

        try {
            await fetch('/api/cancel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ orderId: orderId })
            });
            cancelOrderIdInput.value = '';
        } catch (err) {
            console.error('Failed to cancel order:', err);
        }
    });

    // Connect Server-Sent Events (SSE) Stream
    function connectSse() {
        const evtSource = new EventSource('/api/events');

        evtSource.addEventListener('snapshot', (e) => {
            try {
                const data = JSON.parse(e.data);
                renderSnapshot(data);
            } catch (err) {
                console.error('Error parsing snapshot data:', err);
            }
        });

        evtSource.addEventListener('trade', (e) => {
            try {
                const trade = JSON.parse(e.data);
                renderTrade(trade);
            } catch (err) {
                console.error('Error parsing trade data:', err);
            }
        });

        evtSource.onerror = (err) => {
            console.warn('SSE connection disconnected. Reconnecting...');
        };
    }

    function renderSnapshot(data) {
        if (data.symbol) valSymbol.textContent = data.symbol;
        if (data.bestBid !== undefined) valBestBid.textContent = '$' + data.bestBid.toFixed(2);
        if (data.bestAsk !== undefined) valBestAsk.textContent = '$' + data.bestAsk.toFixed(2);
        if (data.spread !== undefined) valSpread.textContent = '$' + data.spread.toFixed(2);
        if (data.processedOrders !== undefined) valProcessed.textContent = data.processedOrders.toLocaleString();

        const bids = data.bids || [];
        const asks = data.asks || [];

        if (bids.length === 0 && asks.length === 0) {
            ladderRows.innerHTML = '<div class="empty-state">Order book is empty</div>';
            return;
        }

        let maxVolume = 1;
        for (const [p, v] of bids) {
            if (v > maxVolume) maxVolume = v;
        }
        for (const [p, v] of asks) {
            if (v > maxVolume) maxVolume = v;
        }

        let html = '';

        // Show Asks descending from highest to lowest (lowest at the bottom right above spread)
        const sortedAsks = [...asks].reverse();
        for (const [price, vol] of sortedAsks) {
            const pct = Math.min(100, Math.round((vol / maxVolume) * 100));
            html += `
            <div class="ladder-row">
                <span class="col-bid-qty"></span>
                <span class="depth-bar-container"></span>
                <span class="col-price" style="color: var(--ask-color);">$${price.toFixed(2)}</span>
                <span class="depth-bar-container">
                    <div class="depth-bar ask" style="width: ${pct}%"></div>
                </span>
                <span class="col-ask-qty">${vol.toLocaleString()}</span>
            </div>`;
        }

        // Show Bids descending from highest (best bid) down
        for (const [price, vol] of bids) {
            const pct = Math.min(100, Math.round((vol / maxVolume) * 100));
            html += `
            <div class="ladder-row">
                <span class="col-bid-qty">${vol.toLocaleString()}</span>
                <span class="depth-bar-container">
                    <div class="depth-bar bid" style="width: ${pct}%"></div>
                </span>
                <span class="col-price" style="color: var(--bid-color);">$${price.toFixed(2)}</span>
                <span class="depth-bar-container"></span>
                <span class="col-ask-qty"></span>
            </div>`;
        }

        ladderRows.innerHTML = html;
    }

    function renderTrade(trade) {
        const empty = tapeList.querySelector('.empty-state');
        if (empty) {
            tapeList.removeChild(empty);
        }

        const row = document.createElement('div');
        const sideClass = (trade.side === 'BID' || trade.side === 'BUY') ? 'bid' : 'ask';
        row.className = `tape-row ${sideClass}`;

        const timeStr = new Date().toLocaleTimeString();
        row.innerHTML = `
            <span>${timeStr}</span>
            <span>${trade.side}</span>
            <span>$${trade.price.toFixed(2)}</span>
            <span>${trade.qty.toLocaleString()}</span>
        `;

        tapeList.insertBefore(row, tapeList.firstChild);

        // Keep maximum 50 rows in tape
        while (tapeList.children.length > 50) {
            tapeList.removeChild(tapeList.lastChild);
        }
    }

    // Initial SSE connection
    connectSse();
})();
