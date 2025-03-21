const http = require('http');
const fs = require('fs').promises;
const path = require('path');

const rankFile = path.join('D:/Project/darkpixel/data', 'ranks.json');
const groupFile = path.join('D:/Project/darkpixel/data', 'groups.json');
const onlinePlayersFile = path.join('D:/Project/darkpixel/data', 'online_players.json');
const port = 8080;

const server = http.createServer(async (req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    if (req.url === '/ranks' && req.method === 'GET') {
        try {
            const data = await fs.readFile(rankFile, 'utf8');
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(data);
        } catch (err) {
            res.writeHead(500, { 'Content-Type': 'text/plain' });
            res.end('Error reading ranks.json');
        }
    }

    else if (req.url === '/groups' && req.method === 'GET') {
        try {
            const data = await fs.readFile(groupFile, 'utf8');
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(data);
        } catch (err) {
            res.writeHead(500, { 'Content-Type': 'text/plain' });
            res.end('Error reading groups.json');
        }
    }

    else if (req.url === '/groups' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', async () => {
            try {
                const groups = JSON.parse(body);
                await fs.writeFile(groupFile, JSON.stringify(groups, null, 2), 'utf8');
                res.writeHead(200, { 'Content-Type': 'text/plain' });
                res.end('Groups updated successfully');
            } catch (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain' });
                res.end('Error writing groups.json');
            }
        });
    }

    else if (req.url === '/groups/create' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', async () => {
            try {
                const { groupName } = JSON.parse(body);
                const groupsData = await fs.readFile(groupFile, 'utf8');
                const data = JSON.parse(groupsData);
                if (!data.groups[groupName]) {
                    data.groups[groupName] = { name: groupName, color: '', emoji: '', badge: '' };
                    await fs.writeFile(groupFile, JSON.stringify(data, null, 2), 'utf8');
                    res.writeHead(200, { 'Content-Type': 'text/plain' });
                    res.end('Group created successfully');
                } else {
                    res.writeHead(400, { 'Content-Type': 'text/plain' });
                    res.end('Group already exists');
                }
            } catch (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain' });
                res.end('Error creating group');
            }
        });
    }

    else if (req.url === '/groups/edit' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', async () => {
            try {
                const { groupName, color, emoji, badge } = JSON.parse(body);
                const groupsData = await fs.readFile(groupFile, 'utf8');
                const data = JSON.parse(groupsData);
                if (data.groups[groupName]) {
                    data.groups[groupName].color = color || '';
                    data.groups[groupName].emoji = emoji || '';
                    data.groups[groupName].badge = badge || '';
                    await fs.writeFile(groupFile, JSON.stringify(data, null, 2), 'utf8');
                    res.writeHead(200, { 'Content-Type': 'text/plain' });
                    res.end('Group updated successfully');
                } else {
                    res.writeHead(404, { 'Content-Type': 'text/plain' });
                    res.end('Group not found');
                }
            } catch (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain' });
                res.end('Error updating group');
            }
        });
    }

    else if (req.url === '/online-players' && req.method === 'GET') {
        try {
            const onlinePlayersData = await fs.readFile(onlinePlayersFile, 'utf8');
            const onlinePlayers = JSON.parse(onlinePlayersData);
            const ranksData = await fs.readFile(rankFile, 'utf8');
            const ranks = JSON.parse(ranksData);
            const playersWithDetails = onlinePlayers.map(player => {
                const uuid = player.uuid;
                const rankData = ranks[uuid] || { name: player.name, rank: 'member', score: 0 };
                return {
                    uuid: player.uuid,
                    name: player.name,
                    rank: rankData.rank,
                    score: rankData.score,
                    avatar: `https://minotar.net/avatar/${player.name}/32.png`
                };
            });
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify(playersWithDetails));
        } catch (err) {
            res.writeHead(500, { 'Content-Type': 'text/plain' });
            res.end('Error reading online players');
        }
    }

    else if (req.url === '/toggle-effects' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', async () => {
            try {
                const data = JSON.parse(body);
                const uuid = data.uuid;
                const ranksData = await fs.readFile(rankFile, 'utf8');
                const ranks = JSON.parse(ranksData);
                if (ranks[uuid]) {
                    ranks[uuid].enableEffects = !ranks[uuid].enableEffects;
                    await fs.writeFile(rankFile, JSON.stringify(ranks, null, 2), 'utf8');
                }
                res.writeHead(200, { 'Content-Type': 'text/plain' });
                res.end('Effects toggled successfully');
            } catch (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain' });
                res.end('Error toggling effects');
            }
        });
    }

    else {
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end('Not Found');
    }
});

server.listen(port, () => {
    console.log(`Server running at http://localhost:${port}/`);
});