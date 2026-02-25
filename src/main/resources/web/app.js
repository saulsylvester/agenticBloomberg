const state = {
  stories: [],
  selectedStoryId: null,
  selectedStory: null,
  selectedInsights: null,
  portfolio: null,
  storyInsightsCache: new Map(),
  lastRenderedSymbol: '',
};

const currency = new Intl.NumberFormat('en-GB', {
  style: 'currency',
  currency: 'GBP',
  maximumFractionDigits: 2,
});

const number = new Intl.NumberFormat('en-GB', { maximumFractionDigits: 2 });

const feedStatus = document.getElementById('feedStatus');
const storiesList = document.getElementById('storiesList');
const storyCount = document.getElementById('storyCount');
const storyView = document.getElementById('storyView');
const sourceLink = document.getElementById('sourceLink');
const recommendationCards = document.getElementById('recommendationCards');
const ticketStory = document.getElementById('ticketStory');
const stockSymbolHint = document.getElementById('stockSymbolHint');
const tradeForm = document.getElementById('tradeForm');
const tradeMessage = document.getElementById('tradeMessage');
const metricCards = document.getElementById('metricCards');
const positionsBody = document.getElementById('positionsBody');
const tradesTape = document.getElementById('tradesTape');
const equityChart = document.getElementById('equityChart');
const stockChart = document.getElementById('stockChart');

const symbolInput = document.getElementById('symbol');
const sideInput = document.getElementById('side');
const quantityInput = document.getElementById('quantity');
const priceInput = document.getElementById('price');
const noteInput = document.getElementById('note');

async function init() {
  bindEvents();
  await Promise.all([loadStories(), loadPortfolio()]);
}

function bindEvents() {
  document
    .getElementById('refreshFeed')
    .addEventListener('click', () => loadStories(true));

  tradeForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearTradeMessage();

    const payload = {
      symbol: symbolInput.value.trim(),
      side: sideInput.value,
      quantity: Number(quantityInput.value),
      price: Number(priceInput.value),
      note: noteInput.value.trim(),
      storyId: state.selectedStory?.id ?? '',
      storyTitle: state.selectedStory?.title ?? '',
    };

    try {
      const response = await fetch('/api/trades', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const body = await response.json();

      if (!response.ok) {
        throw new Error(body.error || 'Trade failed');
      }

      state.portfolio = body.portfolio;
      renderPortfolio();
      showTradeMessage(
        `Executed ${payload.side} ${payload.quantity} ${payload.symbol} @ ${currency.format(payload.price)}.`,
        false
      );
    } catch (error) {
      showTradeMessage(error.message, true);
    }
  });
}

async function loadStories(showRefreshStatus = false) {
  try {
    setFeedStatus(showRefreshStatus ? 'Refreshing feed...' : 'Loading feed...');

    const response = await fetch('/api/stories');
    if (!response.ok) {
      throw new Error('Unable to load stories');
    }

    const stories = await response.json();
    state.stories = stories;
    storyCount.textContent = `${stories.length} stories`;
    renderStories();

    if (stories.length === 0) {
      setFeedStatus('No stories available');
      return;
    }

    const defaultStory = stories.find((story) => story.id === state.selectedStoryId) ?? stories[0];
    await selectStory(defaultStory.id);
    setFeedStatus(`Feed updated ${new Date().toLocaleTimeString()}`);
  } catch (error) {
    setFeedStatus(error.message);
  }
}

function renderStories() {
  storiesList.innerHTML = '';

  for (const story of state.stories) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = `story-card${story.id === state.selectedStoryId ? ' active' : ''}`;
    card.innerHTML = `
      <h4>${escapeHtml(story.title)}</h4>
      <p>${escapeHtml(story.summary || 'Open story for details.')}</p>
      <div class="story-meta">
        <span>${escapeHtml(story.source || 'BBC News')}</span>
        <span>${formatTime(story.publishedAt)}</span>
      </div>
    `;
    card.addEventListener('click', () => selectStory(story.id));
    storiesList.append(card);
  }
}

async function selectStory(storyId) {
  if (!storyId) {
    return;
  }

  state.selectedStoryId = storyId;
  state.selectedStory = state.stories.find((story) => story.id === storyId) || null;
  renderStories();
  ticketStory.textContent = state.selectedStory ? `Ticket linked: ${state.selectedStory.title}` : 'No story selected';
  clearTradeMessage();

  const listSymbol = normalizeText(state.selectedStory?.suggestedSymbol || '');
  applyTradeSymbolHint(listSymbol);
  if (listSymbol) {
    renderStockPanel(listSymbol);
    stockSymbolHint.textContent = `Symbol: ${listSymbol}`;
  } else {
    stockSymbolHint.textContent = 'Detecting symbol...';
    renderStockPanel('');
  }

  const cached = state.storyInsightsCache.get(storyId);
  if (cached) {
    state.selectedInsights = cached;
    renderStoryInsights();
    return;
  }

  try {
    const response = await fetch(`/api/story?id=${encodeURIComponent(storyId)}`);
    const body = await response.json();
    if (!response.ok) {
      throw new Error(body.error || 'Unable to load story');
    }

    state.storyInsightsCache.set(storyId, body);
    state.selectedInsights = body;
    renderStoryInsights();
  } catch (error) {
    storyView.innerHTML = `<p class="placeholder">${escapeHtml(error.message)}</p>`;
    recommendationCards.innerHTML = '';
    applyTradeSymbolHint(listSymbol);
    renderStockPanel(listSymbol);
  }
}

function renderStoryInsights() {
  if (!state.selectedInsights) {
    return;
  }

  const detail = state.selectedInsights.story || {};
  const bodyText = detail.body || '';
  const paragraphs = bodyText
    .split(/\n\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => `<p>${escapeHtml(line)}</p>`)
    .join('');

  storyView.innerHTML = `
    <h4>${escapeHtml(detail.title)}</h4>
    <p class="meta">${escapeHtml(detail.source)} • ${formatTime(detail.publishedAt)}</p>
    ${paragraphs || `<p class="placeholder">Story text unavailable for this article.</p>`}
  `;

  sourceLink.href = detail.url || '#';
  sourceLink.textContent = detail.url ? 'Open Source' : 'No Source URL';

  const recommendations = state.selectedInsights.recommendations || [];
  renderRecommendationCards(recommendations);

  const primary = recommendations[0] || null;
  const detectedSymbol =
    normalizeText(detail.suggestedSymbol || '') ||
    normalizeText(primary?.suggestedSymbol || '') ||
    normalizeText(state.selectedStory?.suggestedSymbol || '');

  if (detectedSymbol) {
    applyTradeSymbolHint(detectedSymbol, primary);
    loadPrimaryRecommendation(primary);
    renderStockPanel(detectedSymbol, primary);
  } else {
    stockSymbolHint.textContent = 'No symbol detected';
    renderStockPanel('');
  }
}

function loadPrimaryRecommendation(recommendation) {
  if (!recommendation) {
    return;
  }
  loadRecommendationToTicket(recommendation);
}

function renderRecommendationCards(recommendations) {
  recommendationCards.innerHTML = '';

  if (!recommendations.length) {
    recommendationCards.innerHTML =
      '<p class="placeholder">No deterministic recommendations were produced for this headline.</p>';
    return;
  }

  for (const recommendation of recommendations) {
    const card = document.createElement('article');
    card.className = 'rec-card';

    const actionClass = recommendation.action.toLowerCase();
    card.innerHTML = `
      <div class="rec-top">
        <strong class="rec-entity">${escapeHtml(recommendation.entity)}</strong>
        <span class="badge ${actionClass}">
          ${escapeHtml(recommendation.action)} ${escapeHtml(recommendation.suggestedSymbol || '')
        }
        </span>
      </div>
      <p>Confidence: ${Math.round((recommendation.confidence || 0) * 100)}%</p>
      <p>${escapeHtml(recommendation.rationale)}</p>
      <button class="btn btn-ghost" type="button">Load To Ticket</button>
    `;

    const button = card.querySelector('button');
    button.addEventListener('click', () => loadRecommendationToTicket(recommendation));
    recommendationCards.append(card);
  }
}

function applyTradeSymbolHint(symbol, recommendation = null) {
  const symbolValue = normalizeText(symbol);
  if (symbolValue) {
    symbolInput.value = symbolValue;
  }

  if (recommendation) {
    sideInput.value =
      recommendation.action === 'SELL'
        ? 'SELL'
        : recommendation.action === 'WATCH'
          ? 'BUY'
          : recommendation.action;
  }
}

function renderStockPanel(symbol, recommendation = null) {
  const normalizedSymbol = normalizeText(symbol);
  if (!normalizedSymbol) {
    stockChart.innerHTML = '';
    state.lastRenderedSymbol = '';
    renderStockHintUnresolved();
    return;
  }

  if (state.lastRenderedSymbol !== normalizedSymbol) {
    state.lastRenderedSymbol = normalizedSymbol;
    applyTradeSymbolHint(normalizedSymbol, recommendation);
  }

  const series = generateSymbolSeries(normalizedSymbol);
  renderStockChart(series);
  setPriceFromSeries(series);
}

function renderStockHintUnresolved() {
  if (!stockSymbolHint.textContent || stockSymbolHint.textContent === 'Detecting symbol...') {
    stockSymbolHint.textContent = 'No symbol detected';
  }
}

function setPriceFromSeries(points) {
  if (points.length === 0) {
    return;
  }

  const lastPrice = points[points.length - 1]?.value;
  if (Number.isFinite(lastPrice)) {
    priceInput.value = String(lastPrice.toFixed(2));
  }
}

function loadRecommendationToTicket(recommendation) {
  const symbol = normalizeText(recommendation.suggestedSymbol || '');
  const action = recommendation.action === 'SELL' ? 'SELL' : 'BUY';

  symbolInput.value = symbol;
  sideInput.value = action;
  quantityInput.value = recommendation.action === 'WATCH' ? 50 : 100;
  noteInput.value = `${recommendation.entity}: ${recommendation.rationale}`;
  renderStockPanel(symbol, recommendation);
}

async function loadPortfolio() {
  try {
    const response = await fetch('/api/portfolio');
    if (!response.ok) {
      throw new Error('Unable to load portfolio');
    }

    state.portfolio = await response.json();
    renderPortfolio();
  } catch (error) {
    metricCards.innerHTML = `<div class="metric"><span class="label">Portfolio</span><span class="value negative">${escapeHtml(
      error.message
    )}</span></div>`;
  }
}

function renderPortfolio() {
  const portfolio = state.portfolio;
  if (!portfolio) {
    return;
  }

  metricCards.innerHTML = '';
  const metrics = [
    { label: 'Starting Cash', value: portfolio.startingCash },
    { label: 'Cash', value: portfolio.cash },
    { label: 'Equity', value: portfolio.equity },
    { label: 'Total P/L', value: portfolio.totalPnl },
    { label: 'Realized P/L', value: portfolio.realizedPnl },
    { label: 'Unrealized P/L', value: portfolio.unrealizedPnl },
  ];

  for (const metric of metrics) {
    const block = document.createElement('div');
    block.className = 'metric';
    const valueClass = metric.value > 0 ? 'positive' : metric.value < 0 ? 'negative' : '';
    block.innerHTML = `
      <span class="label">${escapeHtml(metric.label)}</span>
      <span class="value ${valueClass}">${currency.format(metric.value)}</span>
    `;
    metricCards.append(block);
  }

  renderPositions(portfolio.positions || []);
  renderTrades(portfolio.recentTrades || []);
  renderEquityChart(portfolio.equityTimeline || []);
}

function renderPositions(positions) {
  positionsBody.innerHTML = '';

  if (positions.length === 0) {
    positionsBody.innerHTML = '<tr><td colspan="5" class="placeholder">No open positions</td></tr>';
    return;
  }

  for (const position of positions) {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${escapeHtml(position.symbol)}</td>
      <td>${number.format(position.quantity)}</td>
      <td>${currency.format(position.averagePrice)}</td>
      <td>${currency.format(position.lastPrice)}</td>
      <td class="${position.unrealizedPnl > 0 ? 'positive' : position.unrealizedPnl < 0 ? 'negative' : ''}">
        ${currency.format(position.unrealizedPnl)}
      </td>
    `;
    positionsBody.append(row);
  }
}

function renderTrades(trades) {
  tradesTape.innerHTML = '';

  if (trades.length === 0) {
    tradesTape.innerHTML = '<p class="placeholder">No trades executed yet.</p>';
    return;
  }

  for (const trade of trades) {
    const card = document.createElement('article');
    card.className = 'trade-item';
    card.innerHTML = `
      <div class="top">
        <span>${escapeHtml(trade.tradeId)} • ${escapeHtml(trade.side)}</span>
        <span>${escapeHtml(trade.symbol)}</span>
      </div>
      <div class="meta">
        ${number.format(trade.quantity)} @ ${currency.format(trade.price)} | Equity ${currency.format(trade.equityAfterTrade)}
      </div>
      <div class="meta">${formatTime(trade.timestamp)}${trade.storyTitle ? ` • ${escapeHtml(trade.storyTitle)}` : ''}</div>
    `;
    tradesTape.append(card);
  }
}

function renderEquityChart(points) {
  equityChart.innerHTML = '';

  if (points.length === 0) {
    const axis = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    axis.setAttribute('x1', '0');
    axis.setAttribute('y1', '24');
    axis.setAttribute('x2', '100');
    axis.setAttribute('y2', '24');
    axis.setAttribute('class', 'axis');
    equityChart.append(axis);
    return;
  }

  renderLineSeries(
    points.map((point) => ({
      value: Number(point.equity),
      t: point.timestamp,
    })),
    equityChart,
    'var(--teal)'
  );
}

function renderStockChart(points) {
  stockChart.innerHTML = '';
  if (points.length === 0) {
    const axis = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    axis.setAttribute('x1', '0');
    axis.setAttribute('y1', '24');
    axis.setAttribute('x2', '100');
    axis.setAttribute('y2', '24');
    axis.setAttribute('class', 'axis');
    stockChart.append(axis);
    return;
  }

  renderLineSeries(points, stockChart, '#ffde6d', 24);
}

function renderLineSeries(points, svg, strokeColor, height = 24) {
  const values = points.map((point) => Number(point.value));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = max - min || 1;

  const path = points
    .map((point, index) => {
      const x = points.length === 1 ? 50 : (index / (points.length - 1)) * 100;
      const y = 2 + ((max - point.value) / spread) * height;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');

  const axis = document.createElementNS('http://www.w3.org/2000/svg', 'line');
  axis.setAttribute('x1', '0');
  axis.setAttribute('y1', String(height));
  axis.setAttribute('x2', '100');
  axis.setAttribute('y2', String(height));
  axis.setAttribute('class', 'axis');

  const polyline = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
  polyline.setAttribute('points', path);
  polyline.setAttribute('fill', 'none');
  polyline.setAttribute('stroke', strokeColor);
  polyline.setAttribute('stroke-width', '2');

  svg.append(axis, polyline);
}

function generateSymbolSeries(symbol) {
  const key = normalizeText(symbol);
  if (!key) {
    return [];
  }

  const seed = hashSeed(key);
  const end = Date.now();
  const start = end - 2 * 24 * 60 * 60 * 1000;
  const intervalMinutes = 30;
  const totalSteps = (48 * 60) / intervalMinutes;
  const points = [];

  let price = 100 + (seed % 70);
  let random = seed;
  for (let index = 0; index <= totalSteps; index++) {
    random = linearRandom(random);
    const noise = random / 4294967296 - 0.5;
    const drift = noise * (price * 0.0075);
    const cycle = Math.sin((index / totalSteps) * Math.PI * 1.6) * (price * 0.0018);
    price = Math.max(1, Number((price + drift + cycle).toFixed(2)));
    const ts = start + Math.floor((end - start) * (index / totalSteps));

    points.push({
      t: new Date(ts).toISOString(),
      value: Math.round(price * 100) / 100,
    });
  }

  return points;
}

function linearRandom(seed) {
  return (Math.imul(seed, 1664525) + 1013904223) >>> 0;
}

function hashSeed(text) {
  let hash = 2166136261;
  for (let index = 0; index < text.length; index++) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  return hash >>> 0;
}

function setFeedStatus(message) {
  feedStatus.textContent = message;
}

function showTradeMessage(message, isError) {
  tradeMessage.textContent = message;
  tradeMessage.className = `trade-message ${isError ? 'error' : 'success'}`;
}

function clearTradeMessage() {
  tradeMessage.textContent = '';
  tradeMessage.className = 'trade-message';
}

function formatTime(value) {
  if (!value) {
    return 'n/a';
  }

  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) {
    return value;
  }

  return date.toLocaleString();
}

function normalizeText(value) {
  if (!value) {
    return '';
  }

  return value.trim().toUpperCase();
}

function escapeHtml(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

init();
