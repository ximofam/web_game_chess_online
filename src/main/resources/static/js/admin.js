document.addEventListener('DOMContentLoaded', () => {
  const currentPath = window.location.pathname;
  document.querySelectorAll('#sidebar .nav-link').forEach(link => {
    if (link.getAttribute('href') === currentPath) {
      link.classList.add('active');
    }
  });
});