/* FormSubmit AJAX submission: stays on the Arcana website. */
(() => {
  // The address appears in public JavaScript. A static site cannot hide this.
  // Replace with a FormSubmit invisible-email token when one has been issued.
  const recipient = "arelarmour@gmail.com";
  document.querySelectorAll(".beta-signup-form").forEach(form => {
    const email = form.querySelector('input[name="email"]');
    const trap = form.querySelector('input[name="_honey"]');
    const button = form.querySelector('button[type="submit"]');
    const status = form.querySelector('.beta-status');
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      if (trap.value) return;
      button.disabled = true;
      button.textContent = 'Sending…';
      status.dataset.state = '';
      status.textContent = 'Sending signup request…';
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 25000);
      try {
        const response = await fetch('https://formsubmit.co/ajax/' + recipient, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
          signal: controller.signal,
          body: JSON.stringify({
            email: email.value.trim(),
            _subject: 'Arcana Table — Closed Testing Signup',
            _captcha: 'false',
            _honey: '',
            message: 'Please add me to the Arcana Table Android closed-testing invitation list.'
          })
        });
        const data = await response.json();
        // FormSubmit may return HTTP 200 on failures and string values "true"/"false".
        if (!response.ok || String(data.success).toLowerCase() !== 'true') {
          throw new Error('FormSubmit did not confirm the request');
        }
        form.reset();
        status.dataset.state = 'success';
        status.textContent = 'Request sent! Watch your inbox for a possible invitation.';
      } catch (error) {
        status.dataset.state = 'error';
        status.textContent = 'Unable to confirm your signup. Please try again later. The developer may need to activate the form on its first submission.';
      } finally {
        clearTimeout(timeout);
        button.disabled = false;
        button.textContent = 'Request beta access';
      }
    });
  });
})();
