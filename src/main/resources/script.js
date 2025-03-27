const apiBase = "http://localhost:25567/api";
const particles = ["FIREWORK", "FLAME", "HEART", "NOTE", "SMOKE", "VILLAGER_HAPPY"];
const chatColors = ["normal", "§c", "§b", "§a", "§e", "§d", "rainbow", "random", "gradient"];
let availableGroups = [];
let allPlayers = [];

function fetchPlayers() {
    fetch(`${apiBase}/list_players`)
        .then(res => res.json())
        .then(data => {
            const status = document.getElementById("status");
            status.textContent = data.status === "success" ? "在线" : "离线";
            status.classList.toggle("online", data.status === "success");
            status.classList.toggle("offline", data.status !== "success");
            allPlayers = data.players || [];
            renderPlayers(allPlayers);
        })
        .catch(err => {
            const status = document.getElementById("status");
            status.textContent = "离线 (连接失败)";
            status.classList.add("offline");
            console.error("请求失败:", err);
            renderPlayers([]);
        });
}

function fetchGroups() {
    fetch(`${apiBase}/list_groups`)
        .then(res => res.json())
        .then(data => {
            if (data.status === "success") {
                availableGroups = data.groups.map(g => g.name);
                renderPlayers(allPlayers); // 刷新玩家列表以更新身份组选项
                const groupList = document.getElementById("group-list");
                groupList.innerHTML = "";
                data.groups.forEach(group => {
                    const div = document.createElement("div");
                    div.className = "group-item";
                    div.innerHTML = `
                        <span style="color: ${group.color}">[${group.prefix || group.name}] ${group.name}</span>
                        <button onclick="deleteGroup('${group.name}')" class="btn danger-btn"><i class="fas fa-trash"></i></button>
                    `;
                    groupList.appendChild(div);
                });
            }
        })
        .catch(err => console.error("获取身份组失败:", err));
}

function renderPlayers(players) {
    const playerList = document.getElementById("player-list");
    playerList.innerHTML = players.length === 0 ? "<p class='no-data'>暂无玩家数据</p>" : "";
    players.forEach((player, index) => {
        const card = document.createElement("div");
        card.className = "player-card";
        card.id = `player-${player.uuid}`;
        card.style.animationDelay = `${index * 0.1}s`; // 卡牌逐个出现
        const currentGroup = player.groups && player.groups.length > 0 ? player.groups[0] : "member";
        card.innerHTML = `
            <div class="accordion">
                <input type="checkbox" id="toggle-${player.uuid}" class="accordion-toggle">
                <label for="toggle-${player.uuid}" class="accordion-header">
                    <i class="fas fa-user"></i> 
                    <span class="player-name ${player.online ? 'online' : 'offline'}">${player.name}</span> 
                    (${player.online ? "在线" : "离线"})
                </label>
                <div class="accordion-content">
                    <p>UUID: ${player.uuid}</p>
                    <p>
                        <label>积分:</label>
                        <input type="number" value="${player.score}" id="score-${player.uuid}" class="input-field">
                        <button onclick="setScore('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="score-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>Rank:</label>
                        <select id="rank-${player.uuid}" onchange="toggleCustomRank('${player.uuid}')" class="input-field">
                            <option value="member" ${player.rank === "member" ? "selected" : ""}>Member</option>
                            <option value="VIP" ${player.rank === "VIP" ? "selected" : ""}>VIP</option>
                            <option value="SVIP" ${player.rank === "SVIP" ? "selected" : ""}>SVIP</option>
                            <option value="VVIP" ${player.rank === "VVIP" ? "selected" : ""}>VVIP</option>
                            <option value="UVIP" ${player.rank === "UVIP" ? "selected" : ""}>UVIP</option>
                            <option value="EVIP" ${player.rank === "EVIP" ? "selected" : ""}>EVIP</option>
                            <option value="custom" ${!["member", "VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? "selected" : ""}>自定义</option>
                        </select>
                        <input id="custom-rank-${player.uuid}" placeholder="输入自定义 Rank" value="${!["member", "VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? player.rank : ""}" class="input-field" style="display: ${player.rank === 'custom' || !["member", "VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? 'inline-block' : 'none'};">
                        <button onclick="setRank('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="rank-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>身份组:</label>
                        <select id="group-${player.uuid}" class="input-field">
                            ${availableGroups.map(g => `<option value="${g}" ${currentGroup === g ? "selected" : ""}>${g}</option>`).join("")}
                        </select>
                        <button onclick="setGroup('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="group-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>进服粒子:</label>
                        <select id="particle-${player.uuid}" class="input-field">
                            ${particles.map(p => `<option value="${p}" ${p === player.join_particle ? "selected" : ""}>${p}</option>`).join("")}
                        </select>
                        <button onclick="setParticle('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="particle-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>进服消息:</label>
                        <input id="join-message-${player.uuid}" value="${player.join_message}" class="input-field">
                        <button onclick="setJoinMessage('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="join-message-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>发言颜色:</label>
                        <select id="chat-color-${player.uuid}" class="input-field">
                            ${chatColors.map(c => `<option value="${c}" ${c === player.chat_color ? "selected" : ""}>${c === "normal" ? "默认" : c === "rainbow" ? "彩虹" : c === "random" ? "随机" : c === "gradient" ? "渐变" : c}</option>`).join("")}
                        </select>
                        <button onclick="setChatColor('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="chat-color-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>显示选项:</label>
                        <span class="checkbox-group">
                            <label><input type="checkbox" id="show-rank-${player.uuid}" ${player.show_rank ? "checked" : ""}> Rank</label>
                            <label><input type="checkbox" id="show-vip-${player.uuid}" ${player.show_vip ? "checked" : ""}> VIP</label>
                            <label><input type="checkbox" id="show-group-${player.uuid}" ${player.show_group ? "checked" : ""}> 身份组</label>
                        </span>
                        <button onclick="setDisplayOptions('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="display-options-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>封禁状态: ${player.ban_until === 0 ? "未封禁" : player.ban_until === -1 ? "永久封禁" : new Date(player.ban_until).toLocaleString()}</p>
                    <p>封禁原因: ${player.ban_reason || "无"}</p>
                    <p>
                        <button onclick="banPlayer('${player.uuid}')" class="btn danger-btn"><i class="fas fa-ban"></i> 封禁</button>
                        <button onclick="unbanPlayer('${player.uuid}')" class="btn success-btn"><i class="fas fa-unlock"></i> 解封</button>
                        <span id="ban-status-${player.uuid}" class="status"></span>
                    </p>
                </div>
            </div>
        `;
        playerList.appendChild(card);
    });
}

function searchPlayers() {
    const keyword = document.getElementById("search-input").value.toLowerCase();
    const filteredPlayers = allPlayers.filter(player => player.name.toLowerCase().includes(keyword));
    renderPlayers(filteredPlayers);
}

function toggleCustomRank(uuid) {
    const select = document.getElementById(`rank-${uuid}`);
    const customInput = document.getElementById(`custom-rank-${uuid}`);
    customInput.style.display = select.value === "custom" ? "inline-block" : "none";
}

function showStatus(elementId, message, isError = false) {
    const status = document.getElementById(elementId);
    status.textContent = message;
    status.classList.add("active", isError ? "error" : "success");
    setTimeout(() => status.classList.remove("active", isError ? "error" : "success"), 3000);
}

function setScore(uuid) {
    const score = document.getElementById(`score-${uuid}`).value;
    fetch(`${apiBase}/set_score?player=${uuid}&score=${score}`)
        .then(() => {
            showStatus(`score-status-${uuid}`, "设置成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`score-status-${uuid}`, "设置失败", true));
}

function setRank(uuid) {
    const rank = document.getElementById(`rank-${uuid}`).value === "custom" ?
        document.getElementById(`custom-rank-${uuid}`).value :
        document.getElementById(`rank-${uuid}`).value;
    if (rank) {
        fetch(`${apiBase}/set_rank?player=${uuid}&rank=${rank}`)
            .then(() => {
                showStatus(`rank-status-${uuid}`, "设置成功");
                fetchPlayers();
            })
            .catch(err => showStatus(`rank-status-${uuid}`, "设置失败", true));
    }
}

function setGroup(uuid) {
    const group = document.getElementById(`group-${uuid}`).value;
    if (group) {
        fetch(`${apiBase}/set_group?player=${uuid}&group=${group}`)
            .then(() => {
                showStatus(`group-status-${uuid}`, "设置成功");
                fetchPlayers();
            })
            .catch(err => showStatus(`group-status-${uuid}`, "设置失败", true));
    }
}

function setParticle(uuid) {
    const particle = document.getElementById(`particle-${uuid}`).value;
    fetch(`${apiBase}/set_particle?player=${uuid}&particle=${particle}`)
        .then(() => {
            showStatus(`particle-status-${uuid}`, "设置成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`particle-status-${uuid}`, "设置失败", true));
}

function setJoinMessage(uuid) {
    const message = document.getElementById(`join-message-${uuid}`).value;
    fetch(`${apiBase}/set_join_message?player=${uuid}&message=${message}`)
        .then(() => {
            showStatus(`join-message-status-${uuid}`, "设置成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`join-message-status-${uuid}`, "设置失败", true));
}

function setChatColor(uuid) {
    const chatColor = document.getElementById(`chat-color-${uuid}`).value;
    fetch(`${apiBase}/set_chat_color?player=${uuid}&chat_color=${chatColor}`)
        .then(() => {
            showStatus(`chat-color-status-${uuid}`, "设置成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`chat-color-status-${uuid}`, "设置失败", true));
}

function setDisplayOptions(uuid) {
    const showRank = document.getElementById(`show-rank-${uuid}`).checked;
    const showVip = document.getElementById(`show-vip-${uuid}`).checked;
    const showGroup = document.getElementById(`show-group-${uuid}`).checked;
    fetch(`${apiBase}/set_display_options?player=${uuid}&show_rank=${showRank}&show_vip=${showVip}&show_group=${showGroup}`)
        .then(() => {
            showStatus(`display-options-status-${uuid}`, "设置成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`display-options-status-${uuid}`, "设置失败", true));
}

function banPlayer(uuid) {
    const time = prompt("封禁时间(分钟，-1为永久):", "0");
    if (time === null) return;
    const reason = prompt("封禁原因:", "未指定原因");
    if (reason === null) return;
    fetch(`${apiBase}/ban?player=${uuid}&banTime=${time}&reason=${reason}`)
        .then(() => {
            showStatus(`ban-status-${uuid}`, "封禁成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`ban-status-${uuid}`, "封禁失败", true));
}

function unbanPlayer(uuid) {
    fetch(`${apiBase}/unban?player=${uuid}`)
        .then(() => {
            showStatus(`ban-status-${uuid}`, "解封成功");
            fetchPlayers();
        })
        .catch(err => showStatus(`ban-status-${uuid}`, "解封失败", true));
}

function createGroup() {
    const name = document.getElementById("group-name").value;
    const color = document.getElementById("group-color").value || "§f";
    const emoji = document.getElementById("group-emoji").value;
    const badge = document.getElementById("group-badge").value;
    const prefix = document.getElementById("group-prefix").value;
    if (name) {
        fetch(`${apiBase}/create_group?name=${name}&color=${color}&emoji=${emoji}&badge=${badge}&prefix=${prefix}`)
            .then(() => {
                fetchGroups();
                document.getElementById("group-name").value = "";
                document.getElementById("group-color").value = "";
                document.getElementById("group-emoji").value = "";
                document.getElementById("group-badge").value = "";
                document.getElementById("group-prefix").value = "";
            })
            .catch(err => console.error("创建身份组失败:", err));
    }
}

function deleteGroup(name) {
    if (confirm(`确定删除身份组 ${name} 吗？`)) {
        fetch(`${apiBase}/delete_group?name=${name}`)
            .then(() => fetchGroups())
            .catch(err => console.error("删除身份组失败:", err));
    }
}

// 实时搜索
document.getElementById("search-input").addEventListener("input", searchPlayers);

setInterval(() => {
    fetchPlayers();
    fetchGroups();
}, 5000);
fetchPlayers();
fetchGroups();