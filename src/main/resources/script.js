const apiBase = "http://localhost:25560/api";
const particles = ["FIREWORK", "FLAME", "HEART", "NOTE", "SMOKE", "VILLAGER_HAPPY"];
const chatColors = ["normal", "§c", "§b", "§a", "§e", "§d", "rainbow", "random", "gradient"];
let availableGroups = [];
let allPlayers = [];

const fetchData = async (endpoint) => {
    const response = await fetch(`${apiBase}/${endpoint}`, { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`);
    return response.json();
};

const updateStatus = (statusElement, refreshBtn, isOnline, message) => {
    statusElement.textContent = message;
    statusElement.classList.toggle("online", isOnline);
    statusElement.classList.toggle("offline", !isOnline);
    refreshBtn.classList.remove("refreshing");
};

const fetchPlayers = async () => {
    const statusElement = document.getElementById("status");
    const refreshBtn = document.getElementById("refresh-btn");
    refreshBtn.classList.add("refreshing");

    try {
        const data = await fetchData("list_players?page=1&pageSize=1000");
        updateStatus(statusElement, refreshBtn, data.status === "success", "在线");
        allPlayers = data.players || [];
        renderPlayers(allPlayers);
    } catch (err) {
        console.error("Fetch players error:", err);
        updateStatus(statusElement, refreshBtn, false, "离线 (连接失败)");
        renderPlayers([]);
    }
};

const fetchGroups = async () => {
    try {
        const data = await fetchData("list_groups");
        if (data.status === "success") {
            availableGroups = data.groups.map(g => g.name);
            renderGroups(data.groups);
            renderPlayers(allPlayers);
        }
    } catch (err) {
        console.error("Fetch groups error:", err);
    }
};

const renderGroups = (groups) => {
    const groupList = document.getElementById("group-list");
    groupList.innerHTML = "";
    groups.forEach(group => {
        const div = document.createElement("div");
        div.className = "group-item";
        div.innerHTML = `
            <span style="color: ${group.color}">[${group.prefix || group.name}] ${group.name}</span>
            <button onclick="deleteGroup('${group.name}')" class="btn danger-btn"><i class="fas fa-trash"></i></button>
        `;
        groupList.appendChild(div);
    });
};

const renderPlayers = (players) => {
    const playerList = document.getElementById("player-list");
    playerList.innerHTML = players.length === 0 ? "<p class='no-data'>暂无玩家数据</p>" : "";
    const expandedPlayers = JSON.parse(localStorage.getItem("expandedPlayers") || "[]");

    players.forEach((player, index) => {
        const isExpanded = expandedPlayers.includes(player.uuid);
        const card = document.createElement("div");
        card.className = "player-card";
        card.id = `player-${player.uuid}`;
        card.style.animation = `slideIn 0.3s ease forwards ${index * 0.03}s`;
        const currentGroup = player.groups && player.groups.length > 0 ? player.groups[0] : "member";
        card.innerHTML = `
            <div class="accordion">
                <input type="checkbox" id="toggle-${player.uuid}" class="accordion-toggle" ${isExpanded ? "checked" : ""}>
                <label for="toggle-${player.uuid}" class="accordion-header">
                    <i class="fas fa-user"></i>
                    <span class="player-name ${player.online ? 'online' : 'offline'}">${player.name}</span>
                    (${player.online ? "在线" : "离线"})
                </label>
                <div class="accordion-content">
                    <div class="scroll-content">
                        <p>UUID: ${player.uuid}</p>
                        <p>
                            <label>积分:</label>
                            <input type="number" value="${player.score}" id="score-${player.uuid}" class="input-field" min="0">
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
                            <input id="custom-rank-${player.uuid}" placeholder="输入自定义 Rank" value="${!["member", "VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? player.rank : ""}" class="input-field" style="display: ${["member", "VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? 'none' : 'inline-block'};">
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
                                <label><input type="checkbox" id="show-score-${player.uuid}" ${player.show_score ? "checked" : ""}> 积分</label>
                                <label><input type="checkbox" id="show-group-${player.uuid}" ${player.show_group ? "checked" : ""}> 身份组</label>
                                <label><input type="checkbox" id="show-rank-${player.uuid}" ${player.show_rank ? "checked" : ""}> Rank</label>
                                <label><input type="checkbox" id="show-vip-${player.uuid}" ${player.show_vip ? "checked" : ""}> VIP</label>
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
            </div>
        `;
        playerList.appendChild(card);
        document.getElementById(`toggle-${player.uuid}`).addEventListener("change", () => {
            const expanded = JSON.parse(localStorage.getItem("expandedPlayers") || "[]");
            if (document.getElementById(`toggle-${player.uuid}`).checked) {
                if (!expanded.includes(player.uuid)) expanded.push(player.uuid);
            } else {
                const index = expanded.indexOf(player.uuid);
                if (index > -1) expanded.splice(index, 1);
            }
            localStorage.setItem("expandedPlayers", JSON.stringify(expanded));
        });
    });

    setTimeout(() => playerList.classList.add("visible"), 100);
};

const searchPlayers = debounce(() => {
    const keyword = document.getElementById("search-input").value.toLowerCase();
    const filteredPlayers = allPlayers.filter(player => player.name.toLowerCase().includes(keyword));
    renderPlayers(filteredPlayers);
}, 300);

const toggleCustomRank = (uuid) => {
    const select = document.getElementById(`rank-${uuid}`);
    const customInput = document.getElementById(`custom-rank-${uuid}`);
    customInput.style.display = select.value === "custom" ? "inline-block" : "none";
};

const showStatus = (elementId, message, isError = false) => {
    const status = document.getElementById(elementId);
    status.textContent = message;
    status.classList.add("active", isError ? "error" : "success");
    setTimeout(() => status.classList.remove("active", "error", "success"), 1500);
};

const apiRequest = async (endpoint, successMessage, errorMessage, uuid, statusId) => {
    try {
        await fetch(`${apiBase}/${endpoint}`);
        showStatus(statusId, successMessage);
        await fetchPlayers();
    } catch (err) {
        console.error(`${endpoint} error:`, err);
        showStatus(statusId, errorMessage, true);
    }
};

const setScore = (uuid) => {
    const score = document.getElementById(`score-${uuid}`).value;
    if (score < 0) return showStatus(`score-status-${uuid}`, "积分不能为负", true);
    apiRequest(`set_score?player=${uuid}&score=${score}`, "设置成功", "设置失败", uuid, `score-status-${uuid}`);
};

const setRank = (uuid) => {
    const rank = document.getElementById(`rank-${uuid}`).value === "custom" ?
        document.getElementById(`custom-rank-${uuid}`).value.trim() :
        document.getElementById(`rank-${uuid}`).value;
    if (!rank) return showStatus(`rank-status-${uuid}`, "Rank 不能为空", true);
    apiRequest(`set_rank?player=${uuid}&rank=${encodeURIComponent(rank)}`, "设置成功", "设置失败", uuid, `rank-status-${uuid}`);
};

const setGroup = (uuid) => {
    const group = document.getElementById(`group-${uuid}`).value;
    apiRequest(`set_group?player=${uuid}&group=${group}`, "设置成功", "设置失败", uuid, `group-status-${uuid}`);
};

const setParticle = (uuid) => {
    const particle = document.getElementById(`particle-${uuid}`).value;
    apiRequest(`set_particle?player=${uuid}&particle=${particle}`, "设置成功", "设置失败", uuid, `particle-status-${uuid}`);
};

const setJoinMessage = (uuid) => {
    const message = document.getElementById(`join-message-${uuid}`).value.trim();
    if (!message) return showStatus(`join-message-status-${uuid}`, "消息不能为空", true);
    apiRequest(`set_join_message?player=${uuid}&message=${encodeURIComponent(message)}`, "设置成功", "设置失败", uuid, `join-message-status-${uuid}`);
};

const setChatColor = (uuid) => {
    const chatColor = document.getElementById(`chat-color-${uuid}`).value;
    apiRequest(`set_chat_color?player=${uuid}&chat_color=${chatColor}`, "设置成功", "设置失败", uuid, `chat-color-status-${uuid}`);
};

const setDisplayOptions = (uuid) => {
    const showScore = document.getElementById(`show-score-${uuid}`).checked;
    const showGroup = document.getElementById(`show-group-${uuid}`).checked;
    const showRank = document.getElementById(`show-rank-${uuid}`).checked;
    const showVip = document.getElementById(`show-vip-${uuid}`).checked;
    apiRequest(`set_display_options?player=${uuid}&show_score=${showScore}&show_group=${showGroup}&show_rank=${showRank}&show_vip=${showVip}`, "设置成功", "设置失败", uuid, `display-options-status-${uuid}`);
};

const banPlayer = async (uuid) => {
    const time = prompt("封禁时间(分钟，-1为永久):", "0");
    if (time === null || isNaN(time)) return;
    const reason = prompt("封禁原因:", "未指定原因") || "未指定原因";
    apiRequest(`ban?player=${uuid}&banTime=${time}&reason=${encodeURIComponent(reason)}`, "封禁成功", "封禁失败", uuid, `ban-status-${uuid}`);
};

const unbanPlayer = (uuid) => {
    apiRequest(`unban?player=${uuid}`, "解封成功", "解封失败", uuid, `ban-status-${uuid}`);
};

const createGroup = async (event) => {
    event.preventDefault();
    const name = document.getElementById("group-name").value.trim();
    if (!name) return alert("组名不能为空");
    const color = document.getElementById("group-color").value || "§f";
    const emoji = document.getElementById("group-emoji").value;
    const badge = document.getElementById("group-badge").value;
    const prefix = document.getElementById("group-prefix").value || `[${name}]`;
    try {
        await fetch(`${apiBase}/create_group?name=${encodeURIComponent(name)}&color=${encodeURIComponent(color)}&emoji=${encodeURIComponent(emoji)}&badge=${encodeURIComponent(badge)}&prefix=${encodeURIComponent(prefix)}`);
        await fetchGroups();
        document.getElementById("group-form").reset();
    } catch (err) {
        console.error("Create group error:", err);
        alert("创建身份组失败");
    }
};

const deleteGroup = async (name) => {
    if (!confirm(`确定删除身份组 ${name} 吗？`)) return;
    try {
        await fetch(`${apiBase}/delete_group?name=${encodeURIComponent(name)}`);
        await fetchGroups();
    } catch (err) {
        console.error("Delete group error:", err);
        alert("删除身份组失败");
    }
};

function debounce(func, wait) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}

// 事件监听
document.getElementById("search-input").addEventListener("input", searchPlayers);
document.getElementById("refresh-btn").addEventListener("click", async () => {
    await fetchPlayers();
    await fetchGroups();
});

// 自动刷新
setInterval(async () => {
    await fetchPlayers();
    await fetchGroups();
}, 40000);

// 初始加载
fetchPlayers();
fetchGroups();