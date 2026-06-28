(function () {
  var STORAGE_KEY = 'wartezeiten-theme';

  function currentTheme() {
    var stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'dark';
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
  }

  applyTheme(currentTheme());

  document.addEventListener('DOMContentLoaded', function () {
    var button = document.getElementById('themeToggle');
    if (!button) return;

    function updateButton(theme) {
      button.textContent = theme === 'dark' ? '☀️ Hell' : '🌙 Dunkel';
      button.setAttribute('aria-label', theme === 'dark' ? 'Zu hellem Design wechseln' : 'Zu dunklem Design wechseln');
    }

    updateButton(currentTheme());

    button.addEventListener('click', function () {
      var next = currentTheme() === 'dark' ? 'light' : 'dark';
      localStorage.setItem(STORAGE_KEY, next);
      applyTheme(next);
      updateButton(next);
    });
  });
})();
