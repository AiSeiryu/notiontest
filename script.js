const menuToggle = document.querySelector('.menu-toggle');
const mainNav = document.querySelector('.main-nav');

menuToggle?.addEventListener('click', () => {
  const isOpen = mainNav.classList.toggle('is-open');
  menuToggle.setAttribute('aria-expanded', String(isOpen));
});

document.querySelectorAll('.main-nav a').forEach((link) => {
  link.addEventListener('click', () => {
    mainNav.classList.remove('is-open');
    menuToggle?.setAttribute('aria-expanded', 'false');
  });
});

document.querySelector('#join-form')?.addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const note = document.querySelector('#form-note');
  const button = form.querySelector('button');
  const email = form.querySelector('input');

  if (!email.value) return;
  button.innerHTML = 'Сигнал принят <span>✓</span>';
  button.disabled = true;
  note.textContent = 'Проверь почту — первый сигнал уже на подходе.';
  note.style.color = '#d6f36a';
});

document.querySelector('#year').textContent = new Date().getFullYear();
